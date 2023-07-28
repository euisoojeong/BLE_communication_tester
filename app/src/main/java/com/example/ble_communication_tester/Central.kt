package com.example.ble_communication_tester

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.util.UUID

class Central : AppCompatActivity() {
    private var btManager: BluetoothManager? = null
    private var btAdapter: BluetoothAdapter? = null
    private var btScanner: BluetoothLeScanner? = null
    private var startScanningButton: Button? = null
    private var stopScanningButton: Button? = null
    private var goBackHome: Button? = null
    private var disConnect: Button? = null
    private var peripheralTextView: TextView? = null
    private var setMsg: EditText? = null
    private var sendMsg: Button? = null

    private lateinit var service: BluetoothGattService
    private lateinit var characteristic: BluetoothGattCharacteristic


    var peripheralArr: ArrayList<String> = arrayListOf()

    private lateinit var bluetoothGatt: BluetoothGatt
    private lateinit var bluetoothDevice: BluetoothDevice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_central)

        checkBlePermission()

        peripheralTextView = findViewById<View>(R.id.PeripheralTextView) as TextView
        peripheralTextView!!.movementMethod = ScrollingMovementMethod()

        startScanningButton = findViewById(R.id.StartScanButton)
        startScanningButton!!.setOnClickListener {
            startScanningButton!!.visibility = View.GONE
            stopScanningButton!!.visibility = View.VISIBLE
            startScan()
        }
        stopScanningButton = findViewById(R.id.StopScanButton)
        stopScanningButton!!.setOnClickListener { stopScan() }
        setMsg = findViewById(R.id.ToPeripheralMsgView)
        sendMsg = findViewById(R.id.ToPeripheralMsgBtn)
        sendMsg!!.setOnClickListener {
            if (this::service.isInitialized || this::characteristic.isInitialized) {
                val message = setMsg?.text.toString()
                sendDataToPeripheral(message)
            }
            else { Toast.makeText(this, "연결되지 않았습니다.", Toast.LENGTH_SHORT).show() }
        }

        disConnect = findViewById(R.id.disConnect)
        disConnect!!.setOnClickListener {
            disConnectBLEFromCentral()
        }

        stopScanningButton!!.visibility = View.INVISIBLE
        btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager!!.adapter
        btScanner = btAdapter!!.bluetoothLeScanner

        goBackHome = findViewById(R.id.goBackHome)
        goBackHome?.setOnClickListener {
            if(this::bluetoothGatt.isInitialized) {
                bluetoothGatt.disconnect()
                bluetoothGatt.close()
            }
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun checkBlePermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
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
                    Manifest.permission.BLUETOOTH_SCAN,
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

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            checkConnectPermission()
            bluetoothGatt.discoverServices()
        }

        override fun onConnectionStateChange(
            gatt: BluetoothGatt?,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(gatt, status, newState)
            checkConnectPermission()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    bluetoothGatt.requestMtu(MTU_SIZE)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    runOnUiThread {
                        Toast.makeText(this@Central, "연결이 끊어졌습니다.", Toast.LENGTH_LONG).show()
                    }
                    startScan()
                }
            } else {
                startScan()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                service = gatt!!.getService(SERVICE_UUID)
                characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                checkConnectPermission()
                bluetoothGatt.readCharacteristic(characteristic)
            }
        }

        @Deprecated("Deprecated since API_LEVEL 33")
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            val receivedData = String(characteristic!!.value)
            Log.d(ContentValues.TAG, "onCharacteristicRead: receivedData == $receivedData")
            if(receivedData == "complete") {
                runOnUiThread {
                    disConnect!!.isEnabled = true
                    Toast.makeText(applicationContext, receivedData, Toast.LENGTH_SHORT).show()
                    sendDataToPeripheral("연결되었습니다.")
                }
                stopScan()
            }
        }

        @Deprecated("Deprecated since API_LEVEL 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)
            val message = String(characteristic!!.value)
            runOnUiThread {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            peripheralArr.add(result.device.address)
            checkConnectPermission()
            peripheralTextView!!.append("Device Name: ${result.device.name}, rssi: ${result.rssi}, UUID: ${result.device.uuids}, address: ${result.device.address}\n\n")

            result.device?.let { device ->
                if (device.name == "test_name-change") {
                    bluetoothDevice = device
                    connectToDevice()
                    stopScan()
                }
            }

            val scrollAmount =
                peripheralTextView!!.layout.getLineTop(peripheralTextView!!.lineCount) - peripheralTextView!!.height
            if (scrollAmount > 0) peripheralTextView!!.scrollTo(0, scrollAmount)
        }
    }

    private fun connectToDevice() {
        checkConnectPermission()
        bluetoothGatt = bluetoothDevice.connectGatt(this, false, gattCallback)
    }

    private fun startScan() {
        val filters: MutableList<ScanFilter> = ArrayList()
        val scanFilter: ScanFilter = ScanFilter.Builder()
            .setDeviceName(null)
            .build()
        filters.add(scanFilter)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        peripheralTextView!!.text = ""
        runOnUiThread {
            startScanningButton!!.visibility = View.GONE
            stopScanningButton!!.visibility = View.VISIBLE
        }
        checkConnectPermission()
        btScanner!!.startScan(filters, settings, leScanCallback)
    }

    private fun stopScan() {
        peripheralTextView!!.append("스캔 중지 했음!!!!!")
        runOnUiThread {
            startScanningButton!!.visibility = View.VISIBLE
            stopScanningButton!!.visibility = View.INVISIBLE
        }
        checkConnectPermission()
        btScanner!!.stopScan(leScanCallback)
    }

    private fun sendDataToPeripheral(data: String) {
        val manufacturedData = data.toByteArray()
        if(data.isNotEmpty() && data.isNotBlank()) {
            characteristic.value = manufacturedData
            checkConnectPermission()
            bluetoothGatt.writeCharacteristic(characteristic)
            bluetoothGatt.setCharacteristicNotification(characteristic, true)
        }
        else { Toast.makeText(this, "*** 메세지를 입력해주세요. ***", Toast.LENGTH_SHORT).show() }
    }

    private fun disConnectBLEFromCentral(message: String? = "disConnect") {
        if(message.equals("disConnect")) {
            val disConnMsg = message!!
            sendDataToPeripheral(disConnMsg)
        }
        checkConnectPermission()
        bluetoothGatt.disconnect()
        bluetoothGatt.close()
        disConnect?.isEnabled = false
        runOnUiThread {
            Toast.makeText(this@Central, "연결이 해제되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkConnectPermission() {
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this@Central, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
    }

    companion object {
        // note; MTU 교환 최대로 지원 하는 바이트가 517바이트.
        private const val MTU_SIZE = 517
        private const val PERMISSION_REQUEST_CODE_S = 101
        private const val PERMISSION_REQUEST_CODE = 100
        private val SERVICE_UUID = UUID.fromString("788ce046-166c-4d27-8e16-8c1887810f88")
        private val CHARACTERISTIC_UUID = UUID.fromString("491d8722-e70c-44b4-b0d2-d9d7cc422fec")
    }
}