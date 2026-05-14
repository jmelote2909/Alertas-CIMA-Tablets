package com.example.overlayapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.overlayapp.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val user = binding.etUser.text.toString().trim().lowercase()
            val pass = binding.etPass.text.toString().trim()
            
            if (user == "admin" && pass == "1234") {
                Toast.makeText(this, "Sesión iniciada como Administrador", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, AdminActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
                binding.tilUser.error = "Verificar"
                binding.tilPass.error = "Verificar"
            }
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
