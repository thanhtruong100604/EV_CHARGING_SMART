package com.example.carparkingsmart.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.carparkingsmart.MainActivity
import com.example.carparkingsmart.R
import com.example.carparkingsmart.api.LoginRequest

class LoginActivity : AppCompatActivity() {
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kiểm tra Token cũ (Auto Login)
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        // Nếu đã có token và CŨNG PHẢI có email thì mới cho vào thẳng
        if (sharedPref.getString("token", null) != null && sharedPref.getString("user_email", null) != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvGoToRegister = findViewById<TextView>(R.id.tvGoToRegister)

        btnLogin.setOnClickListener {
            // Sử dụng .trim() để loại bỏ khoảng trắng thừa nếu lỡ tay nhập nhầm
            val user = findViewById<EditText>(R.id.edtUsername).text.toString().trim()
            val pass = findViewById<EditText>(R.id.edtPassword).text.toString().trim()

            if (user.isNotEmpty() && pass.isNotEmpty()) {
                btnLogin.isEnabled = false   // tránh bấm nhiều lần khi máy chủ đang khởi động
                Toast.makeText(this, "Đang đăng nhập...", Toast.LENGTH_SHORT).show()
                viewModel.login(LoginRequest(user, pass))
            } else {
                Toast.makeText(this, "Vui lòng nhập tài khoản và mật khẩu", Toast.LENGTH_SHORT).show()
            }
        }

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        viewModel.loginResult.observe(this) { token ->
            if (token != null) {
                // 1. Lấy email/username người dùng vừa nhập thành công
                val userEmail = findViewById<EditText>(R.id.edtUsername).text.toString().trim()

                // 2. Lưu cả Token và Email vào bộ nhớ máy
                // LƯU Ý: Phải dùng chung tên "UserPrefs" để MainActivity đọc được
                val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString("token", token)
                    putString("user_email", userEmail) // Lưu email ở đây
                    apply()
                }

                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                btnLogin.isEnabled = true
                Toast.makeText(
                    this,
                    viewModel.loginError.value ?: "Đăng nhập thất bại!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}