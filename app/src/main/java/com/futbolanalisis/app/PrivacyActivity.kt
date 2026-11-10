package com.futbolanalisis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.futbolanalisis.app.ui.theme.FutbolTheme

/** Política de privacidad que Health Connect muestra al usuario al conceder permisos. */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FutbolTheme {
                Surface {
                    Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState())) {
                        Text("Privacidad", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Fútbol Análisis lee de Health Connect tu frecuencia cardiaca, FC en reposo, variabilidad " +
                                "de la FC (HRV), saturación de oxígeno, VO2 máx, pasos, entrenamientos y sus rutas GPS.\n\n" +
                                "Estos datos se usan solo para calcular el análisis físico y táctico de tus partidos. " +
                                "Se guardan únicamente en este teléfono, no se envían a ningún servidor y la app no " +
                                "escribe nada en Health Connect.\n\n" +
                                "Puedes revocar los permisos en cualquier momento desde Health Connect.",
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
