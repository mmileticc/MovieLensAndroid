package dev.milinko.movielens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Developer dialog (debug builds only) for choosing the backend URL and managing the saved URLs
 * (see UrlStorage).
 */
@Composable
fun SettingsDialog(
    currentUrl: String,
    savedRoutes: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onAddRoute: (String) -> Unit,
    onDeleteRoute: (String) -> Unit
) {
    var urlText by remember(currentUrl) { mutableStateOf(currentUrl) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backend URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Izaberi sačuvanu rutu ili unesi novu:", fontSize = 14.sp)

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("http://192.168.1.x:8080/") },
                    trailingIcon = {
                        IconButton(onClick = { isDropdownExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Prikaži sačuvane rute")
                        }
                    }
                )

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (savedRoutes.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Nema sačuvanih ruta") },
                            onClick = { isDropdownExpanded = false }
                        )
                    } else {
                        savedRoutes.forEach { route ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(route, modifier = Modifier.weight(1f))
                                        IconButton(onClick = {
                                            onDeleteRoute(route)
                                            if (urlText == route) urlText = currentUrl
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Obriši rutu")
                                        }
                                    }
                                },
                                onClick = {
                                    urlText = route
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onAddRoute(urlText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Dodaj rutu")
                    }
                    Button(
                        onClick = { onSave(urlText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Izaberi rutu")
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Saveti: 'Dodaj rutu' samo pamti adresu. 'Izaberi rutu' je postavlja kao aktivan backend.",
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zatvori")
            }
        }
    )
}