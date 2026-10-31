package com.kakario.synogallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kakario.synogallery.Settings
import com.kakario.synogallery.Syno
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onDone: () -> Unit) {
    var host by remember { mutableStateOf(Settings.host) }
    var account by remember { mutableStateOf(Settings.account) }
    var pass by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var showOtp by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(Settings.autoLogin) }
    var trust by remember { mutableStateOf(Settings.trustAll) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun doLogin() {
        if (host.isBlank() || account.isBlank() || pass.isEmpty()) {
            error = "QuickConnect ID(또는 주소), 아이디, 비밀번호를 모두 입력해 주세요."
            return
        }
        scope.launch {
            loading = true
            error = null
            Settings.trustAll = trust
            Syno.rebuildClient()
            try {
                Syno.login(host, account.trim(), pass, otp.ifBlank { null })
                Settings.host = host.trim()
                Settings.account = account.trim()
                Settings.autoLogin = auto
                Settings.password = if (auto) pass else ""
                onDone()
            } catch (e: Syno.SynoException) {
                if (e.code == 403 || e.code == 404) showOtp = true
                error = e.message
            } catch (e: Exception) {
                error = e.message ?: "연결하지 못했어요."
            } finally {
                loading = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Filled.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Text("시놀 갤러리", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Text(
            "시놀로지 포토(Synology Photos) 계정으로 로그인",
            color = Color.Gray, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        val fieldMod = Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(vertical = 4.dp)
        OutlinedTextField(
            host, { host = it }, fieldMod,
            label = { Text("QuickConnect ID 또는 NAS 주소") },
            placeholder = { Text("예: myid 또는 mynas.synology.me:5001") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(account, { account = it }, fieldMod, label = { Text("아이디") }, singleLine = true)
        OutlinedTextField(
            pass, { pass = it }, fieldMod,
            label = { Text("비밀번호") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (showOtp) {
            OutlinedTextField(
                otp, { otp = it }, fieldMod,
                label = { Text("OTP 코드 (2단계 인증)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        }

        SwitchRow("자동 로그인", auto) { auto = it }
        SwitchRow("인증서 무시 (IP:5001 등 자체 서명 인증서)", trust) { trust = it }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = { doLogin() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(top = 16.dp).height(50.dp),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("로그인")
        }

        Text(
            "QuickConnect ID만 넣으면 집 와이파이·외부망을 알아서 찾아 연결해요.\nDDNS 주소나 IP:포트로 직접 넣어도 돼요 (http 5000, https 5001).",
            color = Color.Gray, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
