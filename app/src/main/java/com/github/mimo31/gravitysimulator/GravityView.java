package com.github.mimo31.gravitysimulator;

import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AlertDialog;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Viktor on 3/20/2016.
 *
 * View for the main field where gravity is simulated.
 */
public class GravityView extends View implements DialogInterface.OnClickListener{

    private final MainActivity attachedTo;
    private final List<GravitationalObject> objects = new ArrayList<>();
    private final GestureDetectorCompat gestureDetector;
    private boolean isAddingObjectValid;
    private boolean positionConfirmed;
    private float confirmHidingState;
    private boolean simulationPaused;
    private int objectInfoIndex = -1;
    private float objectInfoState;
    private GravitationalObject lastObjectInfoShown;
    private boolean changingVelocity;

    public GravityView(MainActivity attachedTo) {
        super(attachedTo.getApplicationContext());
        this.attachedTo = attachedTo;
        this.gestureDetector = new GestureDetectorCompat(attachedTo.getApplicationContext(), new GestureListener(this));
    }

    private void updateObjects(double deltaTime) {
        for (int i = 0; i < this.objects.size(); i++) {
            GravitationalObject currentObject = this.objects.get(i);
            if (currentObject.getMass() != 0) {
                Vector2d totalForce = new Vector2d(0, 0);
                for (int j = 0; j < this.objects.size(); j++) {
                    if (j != i) {
                        totalForce = totalForce.add(currentObject.getGravitationalForce(this.objects.get(j)));
                    }
                }
                currentObject.velocity = currentObject.velocity.add(totalForce.multiply(deltaTime / currentObject.getMass()));
            }
            currentObject.position.x += currentObject.velocity.x * deltaTime;
            currentObject.position.y += currentObject.velocity.y * deltaTime;
            if (currentObject.position.x < currentObject.radius) {
                currentObject.position.x = 2 * currentObject.radius - currentObject.position.x;
                currentObject.velocity.x *= -1;
            }
            if (currentObject.position.x > this.getWidth() - currentObject.radius) {
                currentObject.position.x = 2 * (this.getWidth() - currentObject.radius) - currentObject.position.x;
                currentObject.velocity.x *= -1;
            }
            if (currentObject.position.y < currentObject.radius + this.getHeight() / 16) {
                currentObject.position.y = 2 * (currentObject.radius + this.getHeight() / 16) - currentObject.position.y;
                currentObject.velocity.y *= -1;
            }
            if (currentObject.position.y > this.getHeight() - currentObject.radius) {
                currentObject.position.y = 2 * (this.getHeight() - currentObject.radius) - currentObject.position.y;
                currentObject.velocity.y *= -1;
            }
        }
        for (int i = 0; i < this.objects.size(); i++) {
            for (int j = i + 1; j < this.objects.size(); j++) {
                GravitationalObject o1 = this.objects.get(i);
                GravitationalObject o2 = this.objects.get(j);
                if (o1.doesCollide(o2)) {
                    Vector2d distanceVector = o1.position.subtract(o2.position);
                    double collisionFactor = o1.velocity.subtract(o2.velocity).dot(distanceVector) / distanceVector.dot(distanceVector);
                    Vector2d addVector = distanceVector.multiply(2 / (o1.getMass() + o2.getMass()) * collisionFactor);
                    o1.velocity = o1.velocity.subtract(addVector.multiply(o2.getMass()));
                    o2.velocity = o2.velocity.add(addVector.multiply(o1.getMass()));
                    Vector2d totalShift = distanceVector.multiply((o1.radius + o2.radius) / distanceVector.getLength() * 2 - 2);
                    double o1VelocityFraction = o1.velocity.getLength() / (o1.velocity.getLength() + o2.velocity.getLength());
                    double o2VelocityFraction = 1 - o1VelocityFraction;
                    Vector2d o1Shift = totalShift.multiply(o1VelocityFraction);
                    Vector2d o2Shift = totalShift.multiply(-o2VelocityFraction);
                    o1.position = o1.position.add(o1Shift);
                    o2.position = o2.position.add(o2Shift);
                }
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        canvas.drawRect(new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), p);
        p.setColor(Color.WHITE);
        Rect menuButtonRect = new Rect(0, 0, canvas.getWidth(), canvas.getHeight() / 16);
        canvas.drawRect(menuButtonRect, p);
        for (int i = 0; i < this.objects.size(); i++) {
            this.objects.get(i).draw(canvas, 0);
        }
        if (this.attachedTo.addingObject == null && !this.changingVelocity) {
            p.setColor(Color.BLACK);
            StringDraw.drawMaxString("Menu", menuButtonRect, canvas.getWidth() / 64, canvas, p);
        }
        else if (this.attachedTo.addingObject != null) {
            this.attachedTo.addingObject.draw(canvas, this.isAddingObjectValid ? Color.GREEN : Color.RED);
        }
        if (this.confirmHidingState != 0 || this.attachedTo.addingObject != null) {
            int alpha = this.attachedTo.addingObject != null ? 127 : (int) (this.confirmHidingState * 127);
            p.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawRect(new Rect(0, canvas.getHeight() * 15 / 16, canvas.getWidth(), canvas.getHeight()), p);
            p.setColor(Color.BLACK);
            StringDraw.drawMaxString("Cancel", new Rect(0, canvas.getHeight() * 15 / 16, canvas.getWidth() / 2, canvas.getHeight()), canvas.getHeight() / 64, canvas, p);
            StringDraw.drawMaxString(this.positionConfirmed ? "Confirm velocity" : "Confirm position", new Rect(canvas.getWidth() / 2, canvas.getHeight() * 15 / 16, canvas.getWidth(), canvas.getHeight()), canvas.getHeight() / 64, canvas, p);
        }
        if (this.attachedTo.addingObject != null && this.positionConfirmed) {
            p.setColor(Color.RED);
            drawVelocity(canvas, p, this.attachedTo.addingObject);
        }
        if (this.simulationPaused) {
            p.setColor(Color.argb(63, 255, 255, 255));
            int squareHalfSideLength = canvas.getHeight() / 16;
            canvas.drawRect(new Rect(canvas.getWidth() / 2 - squareHalfSideLength, canvas.getHeight() / 2 - squareHalfSideLength, canvas.getWidth() / 2 - squareHalfSideLength / 2, canvas.getHeight() / 2 + squareHalfSideLength), p);
            canvas.drawRect(new Rect(canvas.getWidth() / 2 + squareHalfSideLength / 2, canvas.getHeight() / 2 - squareHalfSideLength, canvas.getWidth() / 2 + squareHalfSideLength, canvas.getHeight() / 2 + squareHalfSideLength), p);
        }
        if (this.objectInfoState != 0) {
            GravitationalObject objectToUse;
            if (this.objectInfoIndex == -1) {
                objectToUse = this.lastObjectInfoShown;
            }
            else {
                objectToUse = this.objects.get(this.objectInfoIndex);
                p.setColor(Color.RED);
                drawVelocity(canvas, p, objectToUse);
            }
            objectToUse.drawInfo(canvas, MainActivity.getMovableViewPosition(this.objectInfoState, 0));
        }
        if (this.changingVelocity) {
            p.setColor(Color.RED);
            drawVelocity(canvas, p, this.lastObjectInfoShown);
            p.setColor(Color.WHITE);
            Rect confirmButton = new Rect(0, canvas.getHeight() * 15 / 16, canvas.getWidth(), canvas.getHeight());
            canvas.drawRect(confirmButton, p);
            p.setColor(Color.BLACK);
            StringDraw.drawMaxString("Confirm", confirmButton, canvas.getHeight() / 64, canvas, p);
        }
    }

    private void drawVelocity(Canvas canvas, Paint p, GravitationalObject object) {
        if (object.velocity.x != 0 || object.velocity.y != 0) {
            canvas.save();
            float totalSpeed = (float) object.velocity.getLength() * 32;
            canvas.translate((float) object.position.x, (float) object.position.y);
            canvas.rotate((float) (Math.atan2(object.velocity.y, object.velocity.x) / Math.PI * 180));
            canvas.scale(totalSpeed / 1000, totalSpeed / 1000);
            canvas.drawRect(new Rect(0, -50, 800, 50), p);
            Path trianglePath = new Path();
            trianglePath.moveTo(800, -100);
            trianglePath.lineTo(1000, 0);
            trianglePath.lineTo(800, 100);
            trianglePath.lineTo(800, -100);
            trianglePath.close();
            canvas.drawPath(trianglePath, p);
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        if (!this.attachedTo.paused || this.attachedTo.addingObject != null) {
            this.gestureDetector.onTouchEvent(event);
        }
        return true;
    }

    void validateAddingObject() {
        this.isAddingObjectValid = true;
        for (int i = 0; i < this.objects.size(); i++) {
            if (this.attachedTo.addingObject.doesCollide(this.objects.get(i))) {
                this.isAddingObjectValid = false;
            }
        }
    }

    void update() {
        boolean doInvalidate = false;
        if (this.confirmHidingState != 0) {
            this.confirmHidingState -= 0.08;
            if (this.confirmHidingState < 0) {
                this.confirmHidingState = 0;
            }
            doInvalidate = true;
        }
        if (!this.attachedTo.paused && !this.simulationPaused && !this.changingVelocity) {
            for (int i = 0; i < 256; i++) {
                this.updateObjects(1 / (double) 256);
            }
            doInvalidate = true;
        }
        if (this.objectInfoIndex != -1) {
            if (changingVelocity) {
                if (this.objectInfoState != 0) {
                    this.objectInfoState -= 0.08;
                    if (this.objectInfoState < 0) {
                        this.objectInfoState = 0;
                    }
                    doInvalidate = true;
                }
            }
            else {
                if (this.objectInfoState != 1) {
                    this.objectInfoState += 0.08;
                    if (this.objectInfoState > 1) {
                        this.objectInfoState = 1;
                    }
                    doInvalidate = true;
                }
            }
        }
        else if (this.objectInfoState != 0) {
            this.objectInfoState -= 0.08;
            if (this.objectInfoState < 0) {
                this.objectInfoState = 0;
            }
            doInvalidate = true;
        }
        if (doInvalidate) {
            this.postInvalidate();
        }
    }

    private void showObjectInfo(int objectIndex) {
        this.objectInfoIndex = objectIndex;
    }

    private void changeSelectedObjectVelocity() {
        this.changingVelocity = true;
        this.hideObjectInfo();
    }

    private void hideObjectInfo() {
        this.lastObjectInfoShown = this.objects.get(this.objectInfoIndex);
        this.objectInfoIndex = -1;
    }

    private void removeSelectedObject() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this.attachedTo, R.style.DialogTheme);
        alertBuilder.setTitle("Confirm");
        alertBuilder.setMessage("Are you sure you want to remove this the object?");
        alertBuilder.setPositiveButton("YES", this);
        alertBuilder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        alertBuilder.show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        int indexToRemove = this.objectInfoIndex;
        this.hideObjectInfo();
        this.objects.remove(indexToRemove);
        dialog.cancel();
    }

    void clearAllObjects() {
        this.objects.clear();
    }

    private static class GestureListener extends GestureDetector.SimpleOnGestureListener {

        private final GravityView attachedTo;

        public GestureListener(GravityView attachedTo) {
            this.attachedTo = attachedTo;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (this.attachedTo.objectInfoIndex == -1) {
                if (this.attachedTo.changingVelocity) {
                    if (this.attachedTo.objectInfoState == 0) {
                        this.handleChangingVelocityClicks(e);
                    }
                }
                else {
                    if (e.getY() < this.attachedTo.getHeight() / 16 && !this.attachedTo.attachedTo.paused) {
                        this.attachedTo.attachedTo.pause();
                    }
                    if (this.attachedTo.attachedTo.addingObject != null) {
                        if (e.getY() >= this.attachedTo.getHeight() * 15 / 16) {
                            if (e.getX() >= this.attachedTo.getWidth() / 2) {
                                if (this.attachedTo.positionConfirmed) {
                                    this.attachedTo.objects.add(this.attachedTo.attachedTo.addingObject);
                                    this.attachedTo.attachedTo.addingObject = null;
                                    this.attachedTo.confirmHidingState = 1;
                                    this.attachedTo.positionConfirmed = false;
                                    this.attachedTo.attachedTo.paused = false;
                                } else {
                                    if (this.attachedTo.isAddingObjectValid) {
                                        this.attachedTo.positionConfirmed = true;
                                    }
                                }
                                this.attachedTo.postInvalidate();
                            }
                            else {
                                this.attachedTo.attachedTo.cancelAdditionFromGravityView();
                                this.attachedTo.positionConfirmed = false;
                            }
                        }
                        else {
                            if (e.getY() > this.attachedTo.getHeight() / 16) {
                                GravitationalObject addingObject = this.attachedTo.attachedTo.addingObject;
                                if (this.attachedTo.positionConfirmed) {
                                    addingObject.velocity.x = (e.getX() - addingObject.position.x) / 32;
                                    addingObject.velocity.y = (e.getY() - addingObject.position.y) / 32;
                                }
                                else {
                                    addingObject.position.x = e.getX();
                                    addingObject.position.y = e.getY();
                                    if (addingObject.position.x < addingObject.radius) {
                                        addingObject.position.x = addingObject.radius;
                                    }
                                    else if (addingObject.position.x > this.attachedTo.getWidth() - addingObject.radius) {
                                        addingObject.position.x = this.attachedTo.getWidth() - addingObject.radius;
                                    }
                                    if (addingObject.position.y < this.attachedTo.getHeight() / 16 + addingObject.radius) {
                                        addingObject.position.y = this.attachedTo.getHeight() / 16 + addingObject.radius;
                                    }
                                    else if (addingObject.position.y > this.attachedTo.getHeight() - addingObject.radius) {
                                        addingObject.position.y = this.attachedTo.getHeight() - addingObject.radius;
                                    }
                                    this.attachedTo.validateAddingObject();
                                }
                                this.attachedTo.postInvalidate();
                            }
                        }
                    }
                    else {
                        for (int i = 0; i < this.attachedTo.objects.size(); i++) {
                            GravitationalObject currentObject = this.attachedTo.objects.get(i);
                            if (Math.sqrt(Math.pow(e.getX() - currentObject.position.x, 2) + Math.pow(e.getY() - currentObject.position.y, 2)) <= currentObject.radius) {
                                this.attachedTo.showObjectInfo(i);
                                break;
                            }
                        }
                    }
                }
            }
            else {
                if (this.attachedTo.objectInfoState == 1) {
                    this.handleObjectInfoClicks(e);
                }
            }
            return true;
        }

        private void handleObjectInfoClicks(MotionEvent e) {
            if (e.getY() >= this.attachedTo.getHeight() * 5 / 6) {
                if (e.getY() >= this.attachedTo.getHeight() * 11 / 12) {
                    if (e.getX() >= this.attachedTo.getWidth() / 2) {
                        this.attachedTo.removeSelectedObject();
                    }
                    else {
                        this.attachedTo.hideObjectInfo();
                    }
                }
                else {
                    this.attachedTo.changeSelectedObjectVelocity();
                }
            }
        }

        private void handleChangingVelocityClicks(MotionEvent e) {
            if (e.getY() >= this.attachedTo.getHeight() * 15 / 16) {
                this.attachedTo.changingVelocity = false;
                this.attachedTo.postInvalidate();
            }
            else {
                if (e.getY() >= this.attachedTo.getHeight() / 16) {
                    GravitationalObject changingObject = this.attachedTo.lastObjectInfoShown;
                    changingObject.velocity.x = (e.getX() - changingObject.position.x) / (double) 32;
                    changingObject.velocity.y = (e.getY() - changingObject.position.y) / (double) 32;
                    this.attachedTo.postInvalidate();
                }
            }
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            super.onDoubleTap(e);
            if (!this.attachedTo.attachedTo.paused) {
                this.attachedTo.simulationPaused = !this.attachedTo.simulationPaused;
                this.attachedTo.postInvalidate();
            }
            return true;
        }
    }
}