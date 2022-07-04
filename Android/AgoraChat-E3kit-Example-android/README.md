# How to build end-to-end encrypted chat with AgoraChatSDK and Virgil

## Introduction

This AgoraChat-E3kit-Example-android will show you how to apply Virgil’s E3Kit group encryption to your application’s chat messages using AgoraChatSDK.

The AgoraChat-E3kit-Example-android folder contains an AgoraChat-E3kit-Example-android project folder.Contains functions related to registration, login and logout and session list encryption session.

The Device class is used to interact with e3kit and DemoHelper is used to managed the device information

## What is end-to-end encryption?

An encrypted session means that only users on both sides of the chat can see the specific content of the current message after receiving the message (the server does not know the content of the message). It protects the privacy of user chat content very well.

The interaction process is as follows
![AgoraChatEThreeProcess](https://user-images.githubusercontent.com/3213611/165893823-c8045a6c-ceec-44c7-baea-b2ad5c1d9ff0.png)


## How to implement encrypted sessions

### We use the group encryption function of VirgilE3Kit to ensure that users can see the historical messages in the local database.

1. Login and generate the jwt

    We use our own registration and login and then use the token generator of VirgilE3Kit to generate a jwt and then generate the VirgilE3KitSDK object according to the jwt.

2. Register user

    Use this EThree object to register the current user with VirgilE3Kit.

3. Get user's card

    Get the current user's Card object.

4. Create or load Virgil group

    Use the E3 object to create or load a VirgilE3Kit.Group object based on the session creator's Card object and the sessionId(This group id is a string sorted by the id of the message sender and the message receiver plus the AgoraChat string).

5. Encrypt and decrypt messages

    Use the Group object to encrypt and decrypt the corresponding message.

### Step 1.Login and generate the jwt

#### Login with AgoraChatSDK

register with agoraID and password according Agora SDK server

src/main/java/io/agora/e3kitdemo/ui/LoginActivity.kt
```kotlin
//register
private fun register(
    username: String,
    password: String,
    callBack: CallBack?
) {
    ThreadManager.instance?.runOnIOThread(Runnable {
        try {
            val headers: MutableMap<String, String> =
                HashMap()
            headers["Content-Type"] = "application/json"
            val request = JSONObject()
            request.putOpt("userAccount", username)
            request.putOpt("userPassword", password)
            val url =
                BuildConfig.APP_SERVER_PROTOCOL + "://" + BuildConfig.APP_SERVER_DOMAIN + BuildConfig.APP_SERVER_REGISTER
            val response = HttpClientManager.httpExecute(
                url,
                headers,
                request.toString(),
                HttpClientManager.Method_POST
            )
            val code = response.code
            val responseInfo = response.content
            if (code == 200) {
                if (responseInfo != null && responseInfo.isNotEmpty()) {
                    callBack!!.onSuccess()
                } else {
                    callBack!!.onError(code, responseInfo)
                }
            } else {
                callBack!!.onError(code, responseInfo)
            }
        } catch (e: Exception) {
            e.printStackTrace();
            callBack!!.onError(Error.NETWORK_ERROR, e.message)
        }
    })
}

//login
private fun loginToAppServer(
    username: String,
    password: String,
    callBack: ResultCallBack<LoginBean>?
) {
    ThreadManager.instance?.runOnIOThread(Runnable {
        try {
            val headers: MutableMap<String, String> =
                HashMap()
            headers["Content-Type"] = "application/json"
            val request = JSONObject()
            request.putOpt("userAccount", username)
            request.putOpt("userPassword", password)
            val url =
                BuildConfig.APP_SERVER_PROTOCOL + "://" + BuildConfig.APP_SERVER_DOMAIN + BuildConfig.APP_SERVER_URL
            val response = HttpClientManager.httpExecute(
                url,
                headers,
                request.toString(),
                HttpClientManager.Method_POST
            )
            val code = response.code
            val responseInfo = response.content
            if (code == 200) {
                if (responseInfo != null && responseInfo.isNotEmpty()) {
                    val `object` = JSONObject(responseInfo)
                    val token = `object`.getString("accessToken")
                    val agoraUid = `object`.getInt("agoraUid")
                    val bean = LoginBean()
                    bean.accessToken = token
                    bean.username = username
                    bean.agoraUid = agoraUid
                    callBack?.onSuccess(bean)
                } else {
                    callBack!!.onError(code, responseInfo)
                }
            } else {
                callBack!!.onError(code, responseInfo)
            }
        } catch (e: Exception) {
            e.printStackTrace();
            callBack!!.onError(Error.NETWORK_ERROR, e.message)
        }
    })
}

loginToAppServer(agoraID, password, object : ResultCallBack<LoginBean>() {
        override fun onSuccess(value: LoginBean) {
            if (!TextUtils.isEmpty(value.accessToken)) {
                ChatClient.getInstance()
                    .loginWithAgoraToken(agoraID, value.accessToken, object : CallBack {
                        override fun onSuccess() {
                        }
                        override fun onError(code: Int, error: String) {
                        }

                        override fun onProgress(progress: Int, status: String) {}
                    })
            }
        }

        override fun onError(error: Int, errorMsg: String) {
           
        }
 })
```
#### Generate the jwt
1.Get authToken with identity according to App Server

src/main/java/io/agora/e3kitdemo/e3kit/Device.kt
```kotlin
fun authenticate(): String {
    val baseUrl = BuildConfig.E3KIT_APP_SERVER + "/authenticate"
    val fullUrl = URL(baseUrl)

    val urlConnection = fullUrl.openConnection() as HttpURLConnection
    urlConnection.doOutput = true
    urlConnection.doInput = true
    urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
    urlConnection.setRequestProperty("Accept", "application/json")
    urlConnection.requestMethod = "POST"

    val cred = JSONObject()

    cred.put("identity", identity)

    val wr = urlConnection.outputStream
    wr.write(cred.toString().toByteArray(charset("UTF-8")))
    wr.close()

    val httpResult = urlConnection.responseCode
    if (httpResult == HttpURLConnection.HTTP_OK) {
        val response =
            InputStreamReader(urlConnection.inputStream, "UTF-8").buffered().use {
                it.readText()
            }

        val jsonObject = JSONObject(response)

        return jsonObject.getString("authToken")
    } else {
        throw Throwable("$httpResult")
    }
}
```

2.Get jwt with authToken according to App Server

src/main/java/io/agora/e3kitdemo/e3kit/Device.kt
```kotlin
fun getVirgilJwt(authToken: String): String {
        try {
            val baseUrl = BuildConfig.E3KIT_APP_SERVER + "/virgil-jwt"
            val fullUrl = URL(baseUrl)

            val urlConnection = fullUrl.openConnection() as HttpURLConnection
            urlConnection.setRequestProperty("Accept", "application/json")
            urlConnection.setRequestProperty("Authorization", "Bearer $authToken")
            urlConnection.requestMethod = "GET"

            val httpResult = urlConnection.responseCode
            if (httpResult == HttpURLConnection.HTTP_OK) {
                val response =
                    InputStreamReader(urlConnection.inputStream, "UTF-8").buffered().use {
                        it.readText()
                    }
                val jsonObject = JSONObject(response)

                return jsonObject.getString("virgilToken")
            } else {
                throw RuntimeException("$httpResult $authToken")
            }
        } catch (e: IOException) {
            throw RuntimeException("$e")
        } catch (e: JSONException) {
            throw RuntimeException("$e")
        }
    }
```

### Step 2.Register user

1.Initialize EThree with jwt and identity

src/main/java/io/agora/e3kitdemo/e3kit/Device.kt
```kotlin
val eThreeParams = EThreeParams(identity, { getVirgilJwt(authToken) }, context)
eThree = EThree(eThreeParams)
```

2.Register user with EThree

src/main/java/io/agora/e3kitdemo/e3kit/Device.kt
```kotlin
eThree.register().addCallback(object : OnCompleteListener {
    override fun onSuccess() {
        _log("Registered")
        callback()
    }

    override fun onError(throwable: Throwable) {
        _log("Failed registering: $throwable")
    }
})
```

### Step 3.Get user's card

Get user's card instance according to EThree and user must be registered

src/main/java/io/agora/e3kitdemo/e3kit/Device.kt
```kotlin
eThree.findUsers(identities).addCallback(object : OnResultListener<FindUsersResult> {
    override fun onError(throwable: Throwable) {
        _log("Failed finding user $identities: $throwable")
        callback(null)
    }

    override fun onSuccess(result: FindUsersResult) {
        _log("Found user $identities")
        callback(result)
    }

})
```

### Step 4.Create or load Virgil group

#### Create Virgil group

Create group instance with groupId and the list of identities,This group id is a string sorted by the id of the message sender and the message receiver plus the AgoraChat string

src/main/java/io/agora/e3kitdemo/ui/ConversationActivity.kt
```kotlin
var groupId = ChatClient.getInstance().currentUser.toLowerCase(Locale.ROOT) + sendTo.toLowerCase(
                            Locale.ROOT
                        ) + "agorachat"
val charArray = groupId.toCharArray()
Arrays.sort(charArray)
groupId = String(charArray)

DemoHelper.demoHelper.createGroup(
    groupId,
    listOf(sendTo)
) {
}
```

src/main/java/io/agora/e3kitdemo/e3kit/Device.kt
```kotlin
fun createGroup(groupId: String, identities: List<String>, callback: (Group?) -> Unit) {
    findUsers(identities) { userResult ->
        if (null != userResult) {
            val group = getEThreeInstance().createGroup(groupId, userResult).get()
            callback(group)
        } else {
            callback(null)
        }
    }
}
```

#### Load Virgil group

Load group instance with groupId and the identity of group initiator,This group id is a string sorted by the id of the message sender and the message receiver plus the AgoraChat string

src/main/java/io/agora/e3kitdemo/ui/ConversationActivity.kt
```kotlin
val conversation = allConversations[conversationId]
val lastMessage = conversation?.lastMessage
if (null != lastMessage) {
    var groupId = lastMessage.from.toLowerCase(Locale.ROOT) + lastMessage.to.toLowerCase(
        Locale.ROOT
    ) + "agorachat"

    var groupInitiator = conversation.extField
    if (groupInitiator.isEmpty()) {
        groupInitiator = lastMessage.from
        ChatClient.getInstance().chatManager().getConversation(conversationId).extField =
            groupInitiator
    }
    val charArray = groupId.toCharArray()
    Arrays.sort(charArray)
    groupId = String(charArray)

    DemoHelper.demoHelper.loadGroup(
        groupId,
        groupInitiator
    ) { group ->
    }
}
```

src/main/java/io/agora/e3kitdemo/e3kit/Device.kt
```kotlin
fun loadGroup(groupId: String, groupInitiator: String, callback: (Group?) -> Unit) {
    findUsers(listOf(groupInitiator)) { userResult ->
        if (null == userResult) {
            callback(null)
        } else {
            val group =
                getEThreeInstance().loadGroup(groupId, userResult[groupInitiator]!!).get()
            callback(group)
        }
    }
}
```

### Step 5.Encrypt and decrypt messages

#### Encrypt message and send message with AgoraChatSDK

src/main/java/io/agora/e3kitdemo/ui/ChatActivity.kt
```kotlin
var messageContent = content;
    if (eGroup != null) {
        messageContent = eGroup!!.encrypt(content)
        EMLog.i(Constants.TAG, "encrypt content:$messageContent")
    }
    val message = ChatMessage.createTxtSendMessage(
        messageContent,
        sendTo
    )
    message.chatType = ChatMessage.ChatType.Chat
    message.setMessageStatusCallback(object : CallBack {
        override fun onSuccess() {
          
        }

        override fun onError(i: Int, s: String) {
            Log.i(Constants.TAG, "send error:$s")
        }

        override fun onProgress(i: Int, s: String) {
        }
    })
    ChatClient.getInstance().chatManager().sendMessage(message)
```

#### Decrypt message

src/main/java/io/agora/e3kitdemo/ui/ChatActivity.kt
```kotlin
holder.sendContent.text =
eGroup!!.decrypt(
    (chatMessage.body as TextMessageBody).message,
    it,
    Date(chatMessage.msgTime)
)
```

## Using

After downloading the code, enter the AgoraChat-E3kit-Example-android folder in the terminal, and run with AndroidStudio

## Quote

> [Virgil Security document](https://developer.virgilsecurity.com/docs/e3kit/fundamentals/cryptography/)
> [Virgil Security github](https://github.com/VirgilSecurity)
## Extension

At present, this demo function supports single chat. If you want to support the group, you can replace the two parameters of the session created in the demo with the group id and the group creator.
