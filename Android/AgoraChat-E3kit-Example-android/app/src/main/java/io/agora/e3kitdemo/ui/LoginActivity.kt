package io.agora.e3kitdemo.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import io.agora.CallBack
import io.agora.Error
import io.agora.chat.ChatClient
import io.agora.cloud.HttpClientManager
import io.agora.e3kitdemo.BuildConfig
import io.agora.e3kitdemo.R
import io.agora.e3kitdemo.base.BaseActivity
import io.agora.e3kitdemo.databinding.ActivityLoginBinding
import io.agora.e3kitdemo.model.LoginBean
import io.agora.e3kitdemo.utils.ResultCallBack
import io.agora.e3kitdemo.utils.ThreadManager
import org.json.JSONObject


class LoginActivity : BaseActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actionBar: ActionBar? = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }
        binding = ActivityLoginBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        initData()
        initListener()
    }

    private fun initListener() {
        binding.btnLogin.setOnClickListener { loginToAgoraChat() }
        binding.btnRegister.setOnClickListener { register() }
        binding.loading.setOnTouchListener { v, event -> true }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish() // back button
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initData() {

    }

    private fun register() {
        setErrorHint("")
        val agoraID: String = binding.etAgoraId.text.toString().trim { it <= ' ' }
        if (TextUtils.isEmpty(agoraID)) {
            setErrorHint(getString(R.string.sign_error_not_id))
            return
        }
        val password: String = binding.etPassword.text.toString().trim { it <= ' ' }
        if (TextUtils.isEmpty(password)) {
            setErrorHint(getString(R.string.sign_error_not_password))
            return
        }
        binding.btnRegister.isEnabled = false
        binding.btnLogin.isEnabled = false
        binding.loading.visibility = View.VISIBLE

        register(agoraID, password, object : CallBack {
            override fun onSuccess() {
                runOnUiThread {
                    binding.loading.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    binding.btnLogin.isEnabled = true
                    setErrorHint("register success")
                }

            }

            override fun onError(code: Int, error: String?) {
                runOnUiThread {
                    binding.loading.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    binding.btnLogin.isEnabled = true
                    error?.let { setErrorHint(it) }
                }
            }
        })
    }

    private fun loginToAgoraChat() {
        setErrorHint("")
        val agoraID: String = binding.etAgoraId.text.toString().trim { it <= ' ' }
        if (TextUtils.isEmpty(agoraID)) {
            setErrorHint(getString(R.string.sign_error_not_id))
            return
        }
        val password: String = binding.etPassword.text.toString().trim { it <= ' ' }
        if (TextUtils.isEmpty(password)) {
            setErrorHint(getString(R.string.sign_error_not_password))
            return
        }
        binding.btnRegister.isEnabled = false
        binding.btnLogin.isEnabled = false
        binding.loading.visibility = View.VISIBLE

        loginToAppServer(agoraID, password, object : ResultCallBack<LoginBean>() {
            override fun onSuccess(value: LoginBean) {
                if (!TextUtils.isEmpty(value.accessToken)) {
                    ChatClient.getInstance()
                        .loginWithAgoraToken(agoraID, value.accessToken, object : CallBack {
                            override fun onSuccess() {
                                runOnUiThread {
                                    binding.loading.visibility = View.GONE
                                    binding.btnRegister.isEnabled = true
                                    binding.btnLogin.isEnabled = true
                                    val intent = Intent(mContext, MainActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                }

                            }

                            override fun onError(code: Int, error: String) {
                                runOnUiThread {
                                    binding.loading.visibility = View.GONE
                                    binding.btnRegister.isEnabled = true
                                    binding.btnLogin.isEnabled = true
                                    setErrorHint(error)
                                }
                            }

                            override fun onProgress(progress: Int, status: String) {}
                        })
                } else {
                    runOnUiThread {
                        binding.loading.visibility = View.GONE
                        binding.btnRegister.isEnabled = true
                        binding.btnLogin.isEnabled = true
                        setErrorHint("login fail")
                    }
                }
            }

            override fun onError(error: Int, errorMsg: String) {
                runOnUiThread {
                    binding.loading.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    binding.btnLogin.isEnabled = true
                    setErrorHint(errorMsg)
                }
            }
        })
    }

    private fun setErrorHint(error: String) {
        binding.tvHint.text = error
        val fail = resources.getDrawable(R.drawable.failed)
        showLeftDrawable(binding.tvHint, fail)
    }

    private fun showLeftDrawable(editText: TextView, left: Drawable?) {
        val content = editText.text.toString().trim { it <= ' ' }
        editText.setCompoundDrawablesWithIntrinsicBounds(
            if (TextUtils.isEmpty(content)) null else left,
            null,
            null,
            null
        )
    }

    private fun loginToAppServer(
        username: String,
        passworkd: String,
        callBack: ResultCallBack<LoginBean>?
    ) {
        ThreadManager.instance?.runOnIOThread(Runnable {
            try {
                val headers: MutableMap<String, String> =
                    HashMap()
                headers["Content-Type"] = "application/json"
                val request = JSONObject()
                request.putOpt("userAccount", username)
                request.putOpt("userPassword", passworkd)
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
}

