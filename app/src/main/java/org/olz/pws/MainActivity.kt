package org.olz.pws

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.io.IOException
import java.util.*
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


class MainActivity : Activity() {

    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    val messaging : BlockingDeque<ByteArray> = LinkedBlockingDeque()

    companion object {
        const val TAG = "MyCarController"
        val MY_UUID = UUID.fromString("fe6e06bc-ac1e-11ea-bb37-0242ac130002")
        val MESSAGE_BUFFERSIZE = 100
    }

    private lateinit var connectThread : ConnectThread

    override fun onDestroy() {
        super.onDestroy()
        connectThread?.stopController()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        val tv1 : TextView = findViewById(R.id.info)
        val tvRotation: TextView = findViewById(R.id.rotation)
        val btnStop : Button = findViewById(R.id.stopButton)
        val btnStart : Button = findViewById(R.id.startButton)
        val btnAi : Button = findViewById(R.id.aiButton)
        val btnRec : Button = findViewById(R.id.recordBtn)
        val btnLd : Button = findViewById(R.id.btnLD)

        Log.i(TAG, "onCreate")

        btnStop.setOnClickListener( {
            connectThread.updateControllerState(ConnectThread.State.STOP)
        })

        btnStart.setOnClickListener({
            connectThread.updateControllerState(ConnectThread.State.START)
        })

        btnAi.setOnClickListener({
            connectThread.updateControllerState(ConnectThread.State.START_AI)
        })

        btnAi.setOnClickListener({
            connectThread.updateControllerState(ConnectThread.State.START_AI)
        })
        btnLd.setOnClickListener({
            connectThread.updateControllerState(ConnectThread.State.START_LD)
        })

        btnRec.setOnClickListener({
            if(connectThread.connected.get()) {
                if (connectThread.recording.get().equals(ConnectThread.RecordingState.STANDBY)) {
                    connectThread.updateControllerRecordingState(ConnectThread.RecordingState.START_RECORDING)
                    btnRec.text = "||"
                }
                if (connectThread.recording.get().equals(ConnectThread.RecordingState.RECORDING)) {
                    connectThread.updateControllerRecordingState(ConnectThread.RecordingState.STOP_RECORDING)
                    btnRec.text = "REC"
                }
            }
        })
        btnAi.setEnabled(false)
        btnRec.setEnabled(false)
        btnStart.setEnabled(false)
        btnStop.setEnabled(false)
        btnLd.setEnabled(false)

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorAccel: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensorGravity: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)


        val orientationListener = object : SensorEventListener {

            var gravity: FloatArray? = null
            var accel: FloatArray? = null
            val R:FloatArray = FloatArray(9)
            val I:FloatArray = FloatArray(9)

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }

            override fun onSensorChanged(event: SensorEvent?) {

                if(event?.sensor?.type == Sensor.TYPE_GRAVITY) {
                    gravity = event?.values.clone()
                }
                if(event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    accel = event?.values.clone()
                }

                if(accel != null && gravity != null) {
                    if(SensorManager.getRotationMatrix(R, I, gravity, accel)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(R, orientation)

                        val angle = 90.0 + Math.toDegrees(orientation[1].toDouble())
                        val speed = 90.0 + Math.toDegrees(orientation[2].toDouble())

                        val data = "%4.2f".format(Locale.US,angle).toByteArray()

                        tvRotation.text = orientation.map { "%.2f".format(it) }?.joinToString(" ") + " %4.2f".format(Locale.US,angle)
                        tv1.text = "%4.2f".format(Locale.US,angle) + " " +  "%4.2f".format(Locale.US,speed)

                        if(messaging.size > MESSAGE_BUFFERSIZE) {
                            messaging.pollLast();
                        }
                        messaging.offer(data)
                    }
                }
            }
        }
        sensorManager.registerListener(orientationListener, sensorAccel, SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(orientationListener, sensorGravity, SENSOR_DELAY_NORMAL)

        if (bluetoothAdapter != null && bluetoothAdapter?.isEnabled ) {

            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
            pairedDevices?.forEach { device ->
                Log.i(TAG, device.name + " " + device.address)
                if (device.name.equals("raspberrypi")) {
                    connectThread = ConnectThread(
                        device,
                        bluetoothAdapter,
                        messaging,
                        { connected: Boolean ->
                            runOnUiThread({
                                btnAi.setEnabled(connected)
                                btnRec.setEnabled(connected)
                                btnStart.setEnabled(connected)
                                btnStop.setEnabled(connected)
                                btnLd.setEnabled(connected)
                            })
                        })
                    AsyncTask.execute(connectThread)

                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    // Shows the system bars by removing all the flags
// except for the ones that make the content appear under the system bars.
    private fun showSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    private class ConnectThread(device: BluetoothDevice, val bluetoothAdapter: BluetoothAdapter, val messaging: BlockingDeque<ByteArray>, val onConnect: (Boolean) -> Unit) : Thread() {
        enum class State {
            STOPPED,
            STOP,
            DRIVING,
            START,
            START_AI,
            AI,
            START_LD,
            LD
        }

        enum class RecordingState {
            START_RECORDING,
            STOP_RECORDING,
            RECORDING,
            STANDBY
        }

        val stopped = AtomicBoolean(false)
        val connected = AtomicBoolean(false)
        val recording = AtomicReference(RecordingState.STANDBY)
        var controllerState = AtomicReference(State.STOPPED)

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(MY_UUID)
        }

        fun updateControllerState(cs : State):Unit {
            Log.i(TAG, "set controler state to ${cs}")
            controllerState.set(cs)
        }

        fun updateControllerRecordingState(rs : RecordingState):Unit {
            Log.i(TAG, "set controler state to ${rs}")
            recording.set(rs)
        }

        override fun run() {
            bluetoothAdapter?.cancelDiscovery()
            mmSocket?.use { socket ->
                while (true) {
                    try {
                        socket.connect()
                        connected.set(true)
                        onConnect(true)
                        manageMyConnectedSocket(socket)
                    } catch (e: IOException) {
                        Log.i(TAG, "Failed to connect")
                        controllerState.set(State.STOPPED)
                        recording.set(RecordingState.STANDBY)
                        connected.set(false)
                        onConnect(false)
                    }
                }
            }
        }

        fun manageMyConnectedSocket(socket: BluetoothSocket) {
            Log.i(TAG, "Connected")
            while (!stopped.get()) {
                if(recording.get().equals(RecordingState.START_RECORDING)) {
                    socket.outputStream.write("S_REC".toByteArray())
                    var b = socket.inputStream.read();
                    while (b != 1) {
                        b = socket.inputStream.read()
                    }
                    recording.set(RecordingState.RECORDING)
                }
                if(recording.get().equals(RecordingState.STOP_RECORDING)) {
                    socket.outputStream.write("Q_REC".toByteArray())
                    var b = socket.inputStream.read();
                    while (b != 1) {
                        b = socket.inputStream.read()
                    }
                    recording.set(RecordingState.STANDBY)
                }

                if (controllerState.get().equals(State.DRIVING)) {
                    val data = messaging.pollLast(100, TimeUnit.MILLISECONDS);
                    if (data != null) {
                        socket.outputStream.write(data)
                    }
                } else if (controllerState.get().equals(State.STOP)) {
                    socket.outputStream.write("STOP!".toByteArray())
                    var b = socket.inputStream.read();
                    while (b != 1) {
                        b = socket.inputStream.read()
                    }
                    controllerState.set(State.STOPPED)
                } else if (controllerState.get().equals(State.START)) {
                    socket.outputStream.write("START".toByteArray())
                    var b = socket.inputStream.read();
                    while (b != 1) {
                        b = socket.inputStream.read()
                    }
                    controllerState.set(State.DRIVING)
                } else if (controllerState.get().equals(State.START_AI)) {
                    socket.outputStream.write("USEAI".toByteArray())
                    var b = socket.inputStream.read();
                    while (b != 1) {
                        b = socket.inputStream.read()
                    }
                    controllerState.set(State.AI)
                } else if (controllerState.get().equals(State.START_LD)) {
                    socket.outputStream.write("USELD".toByteArray())
                    var b = socket.inputStream.read();
                    while (b != 1) {
                        b = socket.inputStream.read()
                    }
                    controllerState.set(State.LD)
                }
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }

        fun stopController() {
            Log.e(TAG, "Stop My Car Controller")
            stopped.set(true)
        }
    }


}