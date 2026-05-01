package com.test.mini

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val apiBase = "http://192.168.5.110:8082"

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var tvResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        tvResult = findViewById(R.id.tvResult)

        btnLogin.setOnClickListener { login() }
        btnRegister.setOnClickListener { register() }
    }

    private fun login() {
        val username = etUsername.text.toString()
        val password = etPassword.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            tvResult.text = "请输入用户名和密码"
            return
        }
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        post("$apiBase/api/auth/login", json) { result ->
            runOnUiThread {
                tvResult.text = result
            }
        }
    }

    private fun register() {
        val username = etUsername.text.toString()
        val password = etPassword.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            tvResult.text = "请输入用户名和密码"
            return
        }
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
            put("email", "$username@test.com")
        }
        post("$apiBase/api/auth/register", json) { result ->
            runOnUiThread {
                tvResult.text = result
            }
        }
    }

    private fun post(url: String, json: JSONObject, callback: (String) -> Unit) {
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("网络错误: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                try {
                    val obj = JSONObject(body)
                    val msg = if (obj.has("message")) obj.getString("message") else obj.toString()
                    callback("code=${obj.getInt("code")} $msg")
                } catch (e: Exception) {
                    callback(body)
                }
            }
        })
    }
}
