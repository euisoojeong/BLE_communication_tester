package com.example.ble_communication_tester

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.util.UUID

class Peripheral : AppCompatActivity() {
    private var btManager: BluetoothManager? = null
    private var btAdapter: BluetoothAdapter? = null
    private var btAdvertiser: BluetoothLeAdvertiser? = null
    private var startAdvertiseButton: Button? = null
    private var stopAdvertiseButton: Button? = null
    private var goBackHome: Button? = null
    private var setMsg: EditText? = null
    private var sendMsg: Button? = null

    private lateinit var service: BluetoothGattService
    private lateinit var characteristic: BluetoothGattCharacteristic
    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothGattServer: BluetoothGattServer



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_peripheral)

        checkBlePermission()

        setMsg = findViewById(R.id.ToCentralMsgView)
        sendMsg = findViewById(R.id.ToCentralMsgBtn)
        sendMsg!!.setOnClickListener {
            if (this::service.isInitialized || this::characteristic.isInitialized) {
                val message = setMsg?.text.toString()
                sendDataToCentral(message)
            }
            else { Toast.makeText(this, "연결되지 않았습니다.", Toast.LENGTH_SHORT).show() }
        }


        startAdvertiseButton = findViewById(R.id.StartAdvertiseButton)
        startAdvertiseButton!!.setOnClickListener {
            resetGattServer()
            startAdvertise()
        }
        stopAdvertiseButton = findViewById(R.id.StopAdvertiseButton)
        stopAdvertiseButton!!.setOnClickListener {
            stopAdvertise()
        }

        btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager!!.adapter
        btAdvertiser = btAdapter!!.bluetoothLeAdvertiser

        goBackHome = findViewById(R.id.goBackHome)
        goBackHome?.setOnClickListener {
            checkConnectPermission()
            if(this::bluetoothGattServer.isInitialized) {
                bluetoothGattServer.cancelConnection(bluetoothDevice)
                bluetoothGattServer.clearServices()
                bluetoothGattServer.close()
            }
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun checkBlePermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED
            ) {
                requestBlePermissions()
                return
            }
        }
        else {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                requestBlePermissions()
                return
            }
        }
    }

    private fun requestBlePermissions(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ),PERMISSION_REQUEST_CODE_S)
        }
        else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                PERMISSION_REQUEST_CODE)
        }
    }

    private fun resetGattServer() {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothAdapter.name = "test_name-change"

        service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)

        checkConnectPermission()
        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        bluetoothGattServer.addService(service)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            checkConnectPermission()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (device != null) {
                        bluetoothDevice = device
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    bluetoothGattServer.cancelConnection(device)
                    bluetoothGattServer.clearServices()
                    bluetoothGattServer.close()
                    runOnUiThread {
                        Toast.makeText(this@Peripheral, "연결이 끊어졌습니다. 재연결을 시도합니다.", Toast.LENGTH_LONG).show()
                    }
                    resetGattServer()
                    startAdvertise()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this@Peripheral, "연결에 실패했습니다. 재연결을 시도합니다.", Toast.LENGTH_LONG).show()
                }
                startAdvertise()
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            checkConnectPermission()
            // note; connect 확인 메세지
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "complete".toByteArray())
            stopAdvertise()
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(
                device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            val receivedData = String(value ?: byteArrayOf())
            if (device != null) {
                bluetoothDevice = device
            }
            if(receivedData == "disConnect") {
                checkConnectPermission()
                bluetoothGattServer.cancelConnection(device)
                bluetoothGattServer.clearServices()
                bluetoothGattServer.close()
            }
            runOnUiThread {
                Toast.makeText(this@Peripheral, receivedData, Toast.LENGTH_SHORT).show()
            }

            // note; 메세지 수신 확인 콜백 메세지
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "메세지 전송 됨".toByteArray())
        }
    }

    private val leAdvertiseCallback: AdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
        }
    }

    private fun startAdvertise() {
        val settings = AdvertiseSettings.Builder()
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        checkConnectPermission()
        btAdvertiser!!.startAdvertising(settings, advertiseData, leAdvertiseCallback)
        runOnUiThread {
            startAdvertiseButton!!.visibility = View.GONE
            stopAdvertiseButton!!.visibility = View.VISIBLE
        }

    }

    private fun stopAdvertise() {
        runOnUiThread {
            startAdvertiseButton!!.visibility = View.VISIBLE
            stopAdvertiseButton!!.visibility = View.GONE
        }
        checkConnectPermission()
        btAdvertiser!!.stopAdvertising(leAdvertiseCallback)
    }

    private fun sendDataToCentral(data: String) {
        val manufacturedData = data.toByteArray()
        characteristic.value = manufacturedData
        checkConnectPermission()
        bluetoothGattServer.notifyCharacteristicChanged(bluetoothDevice, characteristic, false)
    }

    fun checkConnectPermission() {
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this@Peripheral, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE_S = 101
        private const val PERMISSION_REQUEST_CODE = 100
        private val SERVICE_UUID = UUID.fromString("788ce046-166c-4d27-8e16-8c1887810f88")
        private val CHARACTERISTIC_UUID = UUID.fromString("491d8722-e70c-44b4-b0d2-d9d7cc422fec")
    }
}