package com.example.hummerclient.game;

import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.example.hummerclient.MotorActionnable;

public class XboxPad {

    public static int SERVO_MAX_ANGLE = 70;
    public static int MOTOR_MAX_PWM = 127;

    public static Integer ZERO_SPEED = XboxPad.MOTOR_MAX_PWM / 2;
    public static Integer ZERO_ANGLE = XboxPad.SERVO_MAX_ANGLE / 2;

    private int direction = SERVO_MAX_ANGLE / 2; // 35 est le centre du servo
    private float acceleration = 0;
    private int speed = 0;
    private boolean isBraking = false;


    private MotorActionnable motorActionnable;

    public XboxPad(MotorActionnable motorActionnable) {
        this.motorActionnable = motorActionnable;
    }


    public static boolean isDpadDevice(InputEvent event) {
        // Check that input comes from a device with directional pads.
        if ((event.getSource() & InputDevice.SOURCE_DPAD)
                != InputDevice.SOURCE_DPAD) {
            return true;
        } else {
            return false;
        }
    }

    public boolean onGenericMotionEvent(MotionEvent motionEvent) {
        // Check if this event if from a D-pad and process accordingly.
        if (XboxPad.isDpadDevice(motionEvent)) {
            if (!isDpadDevice(motionEvent)) {
                return false;
            }
            // If the input event is a MotionEvent, check its hat axis values.
            if ((motionEvent.getSource() & InputDevice.SOURCE_JOYSTICK) ==
                    InputDevice.SOURCE_JOYSTICK &&
                    motionEvent.getAction() == MotionEvent.ACTION_MOVE) {

//                // Process all historical movement samples in the batch
//                final int historySize = motionEvent.getHistorySize();
//
//                // Process the movements starting from the
//                // earliest historical position in the batch
//                for (int i = 0; i < historySize; i++) {
//                    // Process the event at historical position i
//                    processJoystickInput(motionEvent, i);
//                }

                // Process the current movement sample in the batch (position -1)
                processJoystickInput(motionEvent, -1);
                return true;
            }
        }

        // Check if this event is from a joystick movement and process accordingly.
        return false;
    }


    private void processJoystickInput(MotionEvent event,
                                      int historyPos) {

        InputDevice inputDevice = event.getDevice();

        float x = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_X, historyPos);
        float forwardSpeed = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_GAS, historyPos);
        float backwardSpeed = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_BRAKE, historyPos);

        if (backwardSpeed > 0.0) {
            this.updateSpeed(-backwardSpeed, isBraking);
        } else if (forwardSpeed > 0.0) {
            this.updateSpeed(forwardSpeed, isBraking);
        } else {
            this.updateSpeed(0, isBraking);
        }
        this.updateDirection(x);
    }

    private static float getCenteredAxis(MotionEvent event,
                                         InputDevice device, int axis, int historyPos) {
        final InputDevice.MotionRange range =
                device.getMotionRange(axis, event.getSource());

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            final float flat = range.getFlat();
            final float value =
                    historyPos < 0 ? event.getAxisValue(axis) :
                            event.getHistoricalAxisValue(axis, historyPos);

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }


    private void updateSpeed(float acceleration, boolean isBraking) {

        this.acceleration = acceleration;
        this.isBraking = isBraking;

        int newSpeed = 0;
        if (isBraking) {
            newSpeed = 128; // 0x10000000
        }
        // change the range of the accelaration from [-1, 1] to [0,127]
        //+1 -> [0, 2]
        // * 127 ->[0, 254]
        // /2 -> [ 0, 127]
        if (acceleration < 0) {
            // reculer va de 0 à 64
            newSpeed += Math.round(-acceleration * MOTOR_MAX_PWM / 2);
        } else if (acceleration > 0) {
            // avancer va de 64 à 127
            newSpeed += Math.round(MOTOR_MAX_PWM / 2 + acceleration * MOTOR_MAX_PWM / 2);
        }


        if (this.speed != newSpeed) {

            this.speed = newSpeed;
            motorActionnable.changeSpeed(newSpeed);
        }
    }


    private void updateDirection(float xaxis) {
        // direction need to change xaxis range from [-1.0 , 1.0] to [0, 70]
        int center = SERVO_MAX_ANGLE / 2;
        int newDirection = Math.round((1 + xaxis) * center);

        if (this.direction != newDirection) {
            this.direction = newDirection;
            motorActionnable.changeDirection(newDirection);
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD)
                == InputDevice.SOURCE_GAMEPAD) {
            if (isBrakeKey(keyCode)) {
                this.updateSpeed(acceleration, true);
                return true;
            }
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD)
                == InputDevice.SOURCE_GAMEPAD) {
            if (isBrakeKey(keyCode)) {
                this.updateSpeed(acceleration, false);
                return true;
            }
        }
        return false;
    }

    private static boolean isBrakeKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BUTTON_B;
    }
}