/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2.lutin.fragments

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.luciocoro.lutin.R

/**
 * Este [Fragment] solicita el permiso de uso de cámara y, una vez otorgado, continúa
 * la navegación al próximo fragmento
 * Nota: Última modificación realizada al código: 19/04/24 --> se realizó una corrección al código
 * de pedido de permisos ya que la versión anterior tenía funciones obsoletas
 */
class PermissionsFragment : Fragment() {

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            // Devuelve un booleano que representa si el permiso fue otorgado o no
            if (isGranted) {
                //El permiso fue otorgado, se puede continuar con el flujo de la app
                Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                    PermissionsFragmentDirections.actionPermissionsToSelector())
            } else {
                //El permiso no fue otorgado --> hay que volver a descargar la app
                Toast.makeText(context, "Permiso denegado: no se puede usar la app",
                Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
            requestPermission.launch(Manifest.permission.CAMERA)
    }
}









    /**  CODIGO VIEJO DONDE PEDÍA PERMISO CON FUNCIONES OBSOLETAS
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
        if (hasPermissions(requireContext())) {
            // If permissions have already been granted, proceed
            println("Ya estan los permisos otorgados")
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                       PermissionsFragmentDirections.actionPermissionsToSelector())

        } else {
            // Request camera-related permissions
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        } */
        requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)

    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Takes the user to the success fragment when permission is granted
                Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                        PermissionsFragmentDirections.actionPermissionsToSelector())
            } else {
                Toast.makeText(context, "Permiso denegado", Toast.LENGTH_LONG).show()
            }
        }
    }
*/
    /**
    companion object {

        /** Convenience method used to check if all permissions required by this app are granted */
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
*/