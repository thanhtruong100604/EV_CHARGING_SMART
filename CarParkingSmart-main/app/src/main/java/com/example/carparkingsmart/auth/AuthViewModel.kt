package com.example.carparkingsmart.auth

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carparkingsmart.api.LoginRequest
import com.example.carparkingsmart.api.RegisterRequest
import com.example.carparkingsmart.api.RetrofitClient
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    val loginResult = MutableLiveData<String?>() // Trả về Token hoặc null
    val registerResult = MutableLiveData<Boolean>()
    val loginError = MutableLiveData<String>() // Lý do đăng nhập thất bại, hiện cho người dùng

    // Trong AuthViewModel.kt
    fun login(credentials: LoginRequest) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.login(credentials)
                loginResult.value = response.token
            } catch (e: Exception) {
                // Dòng này sẽ hiện lỗi cụ thể như 404 (sai URL) hoặc 400 (sai mật khẩu)
                android.util.Log.e("LOGIN_DEBUG", "Lỗi: ${e.message}")
                loginError.value = when {
                    e is retrofit2.HttpException && e.code() == 400 -> "Sai tài khoản hoặc mật khẩu"
                    e is retrofit2.HttpException && e.code() == 429 -> "Thử quá nhiều lần, vui lòng đợi 1 phút"
                    else -> "Không kết nối được máy chủ. Máy chủ có thể đang khởi động, hãy thử lại sau ít giây."
                }
                loginResult.value = null
            }
        }
    }

    fun register(request: RegisterRequest) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.register(request)
                registerResult.value = response.id > 0
            } catch (e: Exception) {
                registerResult.value = false
            }
        }
    }


}