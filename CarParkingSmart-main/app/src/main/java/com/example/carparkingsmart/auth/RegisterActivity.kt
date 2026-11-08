package com.example.carparkingsmart.auth

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.carparkingsmart.R
import com.example.carparkingsmart.api.RegisterRequest

class RegisterActivity : AppCompatActivity() {
    // Sử dụng 'by viewModels()' yêu cầu thư viện activity-ktx
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Ánh xạ các View từ XML
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val edtUser = findViewById<EditText>(R.id.edtRegUsername)
        val edtEmail = findViewById<EditText>(R.id.edtRegEmail)
        val edtPass = findViewById<EditText>(R.id.edtRegPassword)

        btnRegister.setOnClickListener {
            val user = edtUser.text.toString().trim()
            val email = edtEmail.text.toString().trim()
            val pass = edtPass.text.toString().trim()

            if (user.isNotEmpty() && email.isNotEmpty() && pass.isNotEmpty()) {
                // Tạo đối tượng Request để gửi lên Server
                val request = RegisterRequest(user, email, pass)
                viewModel.register(request)
            } else {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            }
        }

        // Quan sát kết quả từ ViewModel (PHẢI nằm trong onCreate)
        viewModel.registerResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                finish() // Đóng màn hình này để quay lại Login
            } else {
                Toast.makeText(this, "Đăng ký thất bại, thử lại sau!", Toast.LENGTH_SHORT).show()
            }
        }
    } // Đóng ngoặc onCreate ở đây
}