package com.gwchina.trainee.cropdem;

import android.Manifest;
import android.animation.Animator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * ProjectName CropDemo2
 * PackageName com.gwchina.trainee.cropdemo
 * Created by bosh on 2017/1/12.11:27
 */

/**
 * use "setImage(String path);" to load image to view
 * use "saveCropImagetofile(String path);" to crop
 */
public class CropView extends View {
    private int width;//view宽度
    private int height;//view高度
    private int edgingWidth = 10;//截图框粗细
    private float rectLeft,rectTop,rectRight,rectBottom;//截图框坐标
    private float outWidth;//输出图片宽度
    private float outHeight;//输出图片高度

    private Paint paint=new Paint();
    private static final int detection = 50;//点击位置检测分辨率

    private boolean pad=true;//九宫格
    private boolean isMove =false;
    private boolean mFirst;
    //点击位置
    private static final int LEFT_TOP = 1;
    private static final int LEFT = 2;
    private static final int LEFT_BOTTOM = 3;
    private static final int TOP = 4;
    private static final int BOTTOM = 5;
    private static final int RIGHT_TOP = 6;
    private static final int RIGHT = 7;
    private static final int RIGHT_BOTTOM = 8;
    private static final int OUTSIDE =9;
    private static final int INSIDE =10;
    //图片处理方式
    private static final int ZOOM = 861;
    private static final int TRANSLATE = 185;
    private static final int NONE = 388;
    ValueAnimator animator;
    static int mTime;
    MyThread mThread;
    Thread thread;

    private float preDis;
    private int location;
    private int mode=0;
    private Matrix matrix,scaleMatrix,preMatrix,translateMatrix;
    private PointF middlePoint;
    private Rect src;
    private RectF dst,newDst;
    private float dstLeft,dstTop,dstRight,dstBottom;
    private float x,y;//点击坐标

    private Context mContext;
    private Bitmap mainBitmap;
    private float newBitmapWidth,newBitmapLeft,newBitmapTop,newBitmapRight,newBitmapBottom;

    private final  Handler handler =new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case 1:
                    placeCenter();

            }
            super.handleMessage(msg);
        }
    };

    public class MyThread implements Runnable{
        @Override
        public void run() {
            mTime = 0;
            while (mTime <= 5){
                try {
                    Thread.sleep(100);
                    mTime++;

                }catch (Exception e){
                    break;
                }
            }
            if(mTime >5) {
                Message msg = new Message();
                msg.what = 1;
                handler.sendMessage(msg);
            }
        }
    }


    public CropView(Context context) {
        super(context);
        mContext =context;
        init();
    }

    public CropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext =context;
        init();
    }

    public CropView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext =context;
        init();
    }

    /*public CropView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
    }*/
    private void init(){
        matrix =new Matrix();
        scaleMatrix = new Matrix();
        preMatrix = new Matrix();
        translateMatrix = new Matrix();
        mFirst = true;
        src = new Rect();
        dst = new RectF();
        newDst =new RectF();
        mThread = new MyThread();

        setClickable(true);
        if(ContextCompat.checkSelfPermission(getContext(),
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
                ||ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED
                ||ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS)!=PackageManager.PERMISSION_GRANTED
                ){
            ActivityCompat.requestPermissions((Activity) mContext,
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ,Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS},1);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        width = MeasureSpec.getSize(widthMeasureSpec);
        height = MeasureSpec.getSize(heightMeasureSpec);
        outWidth = width*4/5;//默认大小
        outHeight = height*4/5;
        rectLeft = (width-outWidth)/2;
        rectTop = (height-outHeight)/2;
        rectRight = rectLeft+outWidth;
        rectBottom = rectTop+outHeight;
        dstLeft = rectLeft;
        dstTop = rectTop;
        dstRight = rectRight;
        dstBottom = rectBottom;

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(!isMove){
            super.onDraw(canvas);
        }

        src.set(0,0,mainBitmap.getWidth(),mainBitmap.getHeight());
        dst.set(dstLeft,dstTop,dstRight,dstBottom);
        if(mFirst){
            canvas.drawBitmap(mainBitmap,src,dst,paint);
            mFirst = false;
        }else{
            matrix.mapRect(newDst,dst);
//            Log.i("newrect", newDst.left+"  |  "+newDst.top+"  |  "+newDst.right+"  |  "+newDst.bottom+"");
//            Log.i("oldrect",rectLeft+"  |  "+rectTop+"  |  "+rectRight+"  |  "+rectBottom+"\n");
            newBitmapWidth = newDst.width();
            newBitmapLeft = newDst.left;
            newBitmapTop = newDst.top;
            newBitmapRight = newDst.right;
            newBitmapBottom = newDst.bottom;
            canvas.drawBitmap(mainBitmap,src,newDst,paint);
        }


        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(edgingWidth);
        canvas.drawRect(rectLeft,rectTop,rectRight,rectBottom,paint);

        //九宫格
        if(pad){
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1);
            canvas.drawLine(rectLeft,rectTop+outHeight/3,rectRight,rectTop+outHeight/3,paint);
            canvas.drawLine(rectLeft,rectTop+outHeight*2/3,rectRight,rectTop+outHeight*2/3,paint);
            canvas.drawLine(rectLeft+outWidth/3,rectTop,rectLeft+outWidth/3,rectBottom,paint);
            canvas.drawLine(rectLeft+outWidth*2/3,rectTop,rectLeft+outWidth*2/3,rectBottom,paint);
        }

        //蒙层
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(150);
        paint.setStrokeWidth(0);
        canvas.drawRect(0,0,width,rectTop,paint);
        canvas.drawRect(0,rectBottom,width,height,paint);
        canvas.drawRect(0,rectTop,rectLeft,rectBottom,paint);
        canvas.drawRect(rectRight,rectTop,width,rectBottom,paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float preX,preY,deltaX,deltaY;
//        location = LEFT;
        switch (event.getAction()&MotionEvent.ACTION_MASK){
            case MotionEvent.ACTION_DOWN:
//                animator.start();
                if(animator != null && animator.isRunning()){
                    animator.cancel();
                }
                if(thread!=null){
                    thread.interrupt();
                    thread = null;
                }
                mode = NONE;
//                thread.stop();
                x=event.getX(0);
                y=event.getY(0);
//                Log.e("x:y",x+":"+y);
//                Log.e("左，上，右，下",rectLeft+","+rectTop+","+rectRight+","+rectBottom);
                if(Math.abs((x-rectLeft))<detection){
                    if(Math.abs((y-rectTop))<detection){
                        location = LEFT_TOP;
                    }else if(Math.abs((y-rectBottom))<detection){
                        location = LEFT_BOTTOM;
                    }else{
                        location = LEFT;
                    }
                }else if(Math.abs((x-rectRight))<detection){
                    if(Math.abs((y-rectTop))<detection){
                        location = RIGHT_TOP;
                    }else if(Math.abs((y-rectBottom))<detection){
                        location = RIGHT_BOTTOM;
                    }else{
                        location = RIGHT;
                    }
                }else if(Math.abs((y-rectTop))<detection){
                    location = TOP;
                }else if(Math.abs((y-rectBottom))<detection){
                    location = BOTTOM;
                }else if(x>rectLeft&&y>rectTop&&x<rectRight&&y<rectBottom){
                    location = INSIDE;
                    mode = TRANSLATE;
                }else{
                    location = OUTSIDE;
                    mode = TRANSLATE;
                }

                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                preDis =distance(event);
                middlePoint = middle(event);
                mode = ZOOM;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
            case MotionEvent.ACTION_MOVE:
//                Log.e("move","yes");
                isMove = true;
                if(event.getX(0)<edgingWidth
                        ||event.getY(0)<edgingWidth+height/10
                        ||event.getX(0)>width-edgingWidth
                        ||event.getY(0)>height-edgingWidth-height/10
                        ){
                    if(event.getX(0)<edgingWidth)
                        rectLeft =edgingWidth ;
                    if(event.getY(0)<edgingWidth)
                        rectTop = edgingWidth ;
                    if(event.getX(0)>width-edgingWidth)
                        rectRight = width-edgingWidth;
                    if(event.getY(0)>height-edgingWidth)
                        rectBottom = height-edgingWidth;
                    return true;
                }
                switch(location){
                    case LEFT_TOP:
//                        x=event.getX();
//                        y=event.get
                        rectLeft = event.getX(0);
                        rectTop = event.getY(0);
                        if(rectLeft+detection*3>rectRight
                                ||rectTop+detection*3>rectBottom) {
                            rectLeft = x;
                            rectTop = y;
                        }
                        x=rectLeft;
                        y=rectTop;
                        outWidth = rectRight - rectLeft;
                        outHeight = rectBottom - rectTop;
                        break;
                    case LEFT:
//                        x=event.getX();
//                        y=event.getY();
                        rectLeft = event.getX(0);
                        if(rectLeft+detection*3>rectRight){
                            rectLeft = x;
                        }
                        x = rectLeft;
                        outWidth = rectRight - rectLeft;
                        break;
                    case LEFT_BOTTOM:
//                        x=event.getX();
//                        y=event.getY();
                        rectLeft = event.getX(0);
                        rectBottom = event.getY(0);
                        if(rectLeft+detection*3>rectRight
                                ||rectTop+detection*3>rectBottom) {
                            rectLeft = x;
                            rectBottom = y;
                        }
                        x=rectLeft;
                        y=rectBottom;
                        outWidth = rectRight - rectLeft;
                        outHeight = rectBottom - rectTop;
                        break;
                    case TOP:
//                        x=event.getX();
//                        y=event.getY();
                        rectTop = event.getY(0);
                        if(rectTop+detection*3>rectBottom) {
                            rectTop =  y;
                        }
                        y = rectTop;
                        outHeight = rectBottom - rectTop;
                        break;
                    case BOTTOM:
//                        x=event.getX();
//                        y=event.getY();
                        rectBottom = event.getY(0);
                        if(rectTop+detection*3>rectBottom) {
                            rectBottom = y;
                        }
                        y = rectBottom;
                        outHeight = rectBottom - rectTop;
                        break;
                    case RIGHT_TOP:
//                        x=event.getX();
//                        y=event.getY();
                        rectRight = event.getX(0);
                        rectTop = event.getY(0);
                        if(rectLeft+detection*3>rectRight
                                ||rectTop+detection*3>rectBottom) {
                            rectRight = x;
                            rectTop = y;
                        }
                        x = rectRight;
                        y = rectTop;
                        outWidth = rectRight - rectLeft;
                        outHeight = rectBottom - rectTop;
                        break;
                    case RIGHT:
//                        x=event.getX();
//                        y=event.getY();
                        rectRight = event.getX(0);
                        if(rectLeft+detection*3>rectRight) {
                            rectRight = x;
                        }
                        x = rectRight;
                        outWidth = rectRight - rectLeft;
                        break;
                    case RIGHT_BOTTOM:
//                        x=event.getX();
//                        y=event.getY();
                        rectRight = event.getX(0);
                        rectBottom = event.getY(0);
                        if(rectLeft+detection*3>rectRight
                                ||rectTop+detection*3>rectBottom) {
                            rectRight = x;
                            rectBottom = y;
                        }
                        x= rectRight;
                        y= rectBottom;
                        outWidth = rectRight - rectLeft;
                        outHeight = rectBottom - rectTop;
                        break;
                    case INSIDE:
//                float endX,endY;
//                endX = event.getX(0);
//                endY = event.getY(0);
//                rectLeft = (rectLeft + endX-x);
//                rectTop = (rectTop + endY -y);
//                rectRight = (rectRight + endX -x);
//                rectBottom = (rectBottom + endY-y);
//                if(rectLeft<edgingWidth-3){
//                    rectLeft = edgingWidth-3;
//                            rectTop = (rectTop - endY + y);
//                    rectRight = rectLeft+outWidth;
//                }
//                if(rectTop<edgingWidth-3){
//                    rectTop = edgingWidth-3;
//                    rectBottom = rectTop + outHeight;
//                }
//                if(rectRight>width-edgingWidth+3){
//
//                    rectRight = width - edgingWidth+3;
//                    rectLeft = rectRight -outWidth;
//                }
//                if(rectBottom>height-edgingWidth+3){
//                    rectBottom = height-edgingWidth+3;
//                    rectTop = rectBottom - outHeight;
//                }
//                x = endX;
//                y = endY;
//                break;
                    case OUTSIDE:
                        if(mode == TRANSLATE){
                            preX = x;
                            preY = y;
                            x = event.getX();
                            y = event.getY();
                            deltaX = x - preX;
                            deltaY = y - preY;
                            matrix.postTranslate(deltaX, deltaY);
                        }else if(mode == ZOOM){
                            float dis = distance(event);
                            if(Math.abs(dis-preDis)>10f){
                                float scale = dis / preDis ;
                                matrix.postScale(scale,scale,middlePoint.x,middlePoint.y);
                                preDis = dis;
                            }
                        }else{
                            matrix.set(matrix);
                        }
                        break;
                }
                if(mode == NONE)
                    fullClippingFrame();
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                isMove = false;
                fullClippingFrame();
                thread = new Thread(mThread);
               thread.start();
                /*while(animator.isRunning()){
                    placeCenter();
                }*/
                break;
        }
        return true;
    }

    private float distance(MotionEvent event){
        float x=0,y=0;
        if(event.getPointerCount()>=2){
            x = event.getX(0) - event.getX(1);
            y = event.getY(0) - event.getY(1);
        }
        return (float) Math.sqrt(x*x + y*y);

    }

    private PointF middle(MotionEvent event){
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        return new PointF(x/2,y/2);
    }

    private void fullClippingFrame(){
        float scale=1,dx=0,dy=0;
        if(isFullClippingFrame())
            return;
        preMatrix.set(matrix);
        if(newBitmapWidth < outWidth || newDst.height() < outHeight){
            if(outHeight / newDst.height() < outWidth/newBitmapWidth){
                scale = outWidth / newBitmapWidth;
            }else{
                scale = outHeight / newDst.height();
            }

            if(newBitmapLeft > rectLeft || newBitmapRight < rectRight){
                dx = (rectLeft + rectRight)/2 - (newBitmapLeft + newBitmapRight)/2;
            }
            if(newBitmapTop < rectTop || newBitmapBottom > rectBottom){
                dy = (rectTop + rectBottom)/2 - (newBitmapBottom + newBitmapTop)/2;
            }
        }else{
            if(newBitmapLeft > rectLeft){
                dx = rectLeft - newBitmapLeft;
                Log.i("rectLeft,newBitmapLeft",rectLeft+"  |  "+newBitmapLeft);
            }
            if( newBitmapRight < rectRight){
                dx = rectRight - newBitmapRight;
            }
            if(newBitmapTop > rectTop){
                dy = rectTop - newBitmapTop;
                Log.i("rectTop,newBitmapTop",rectTop+"  |  "+newBitmapTop);
            }
            if(newBitmapBottom < rectBottom){
                dy = rectBottom - newBitmapBottom;
            }
        }

        PropertyValuesHolder scaleFloat = PropertyValuesHolder.ofFloat("scale",1f,scale);
        PropertyValuesHolder translateFloatX = PropertyValuesHolder.ofFloat("translateX",0f,dx);
        PropertyValuesHolder translateFloatY = PropertyValuesHolder.ofFloat("translateY",0f,dy);
        animator = ValueAnimator.ofPropertyValuesHolder(scaleFloat,translateFloatX,translateFloatY).setDuration(100);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float scale =(float) animation.getAnimatedValue("scale");
                float dx = (float) animation.getAnimatedValue("translateX");
                float dy = (float) animation.getAnimatedValue("translateY");
//                Log.i("dy*ratio",dy+"");
//                Log.i("scale",scale+"");

                scaleMatrix.setScale(scale,scale,width/2,height/2);
//                scaleMatrix.setScale(scale,scale,px,py);
//                Log.i("scale,dx,dy",scale+","+dx+","+dy);
                translateMatrix.setTranslate(dx,dy);
                scaleMatrix.preConcat(translateMatrix);
                matrix.setConcat(scaleMatrix,preMatrix);
                invalidate();
            }
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if(!isFullClippingFrame())
                    fullClippingFrame();
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        animator.start();
    }

    private boolean isFullClippingFrame(){
        boolean res = true;
        if(newBitmapWidth < outWidth)
            res = false;
        if(newBitmapLeft > rectLeft || newBitmapRight < rectRight)
            res = false;
        if(newBitmapTop > rectTop || newBitmapBottom < rectBottom)
            res = false;
        return res;
    }

    //截图框居中
    private void placeCenter(){
        preMatrix.set(matrix);
        final RectF rectF = new RectF(rectLeft, rectTop, rectRight, rectBottom);
        float sx;
        //宽极限width-2*edgingWdith,高极限height*4/5
        if((width - 2*edgingWidth)/outWidth < height*4/5/outHeight){
            sx = (width - 2*edgingWidth) / outWidth;
//            Log.i("sx width",sx+"");
        }else{
            sx = height*4/5/outHeight;
//            Log.i("sx height",sx+"");
        }
        final float px = (rectRight + rectLeft)/2;
        final float py = (rectBottom + rectTop)/2;
        float dx = width/2 - px;
        float dy = height/2 - py;
//        Log.i("w,h,dx,dy", width/2+","+height/2+","+dx+","+dy);

        PropertyValuesHolder scaleFloat = PropertyValuesHolder.ofFloat("scale",1f,sx);
        PropertyValuesHolder translateFloatX = PropertyValuesHolder.ofFloat("translateX",0f,dx);
        PropertyValuesHolder translateFloatY = PropertyValuesHolder.ofFloat("translateY",0f,dy);
        PropertyValuesHolder ratioFloat = PropertyValuesHolder.ofFloat("ratio",0f,1f);
        animator = ValueAnimator.ofPropertyValuesHolder(scaleFloat,translateFloatX,translateFloatY,ratioFloat).setDuration(500);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float scale =(float) animation.getAnimatedValue("scale");
                float dx = (float) animation.getAnimatedValue("translateX");
                float dy = (float) animation.getAnimatedValue("translateY");
//                float ratio = (float) animation.getAnimatedValue("ratio");
//                Log.i("dy*ratio",dy+"");
//                Log.i("scale",scale+"");

                scaleMatrix.setScale(scale,scale,width/2,height/2);
//                scaleMatrix.setScale(scale,scale,px,py);
//                Log.i("scale,dx,dy",scale+","+dx+","+dy);
//                Log.i("px,py",px+","+py);
                translateMatrix.setTranslate(dx,dy);
                scaleMatrix.preConcat(translateMatrix);
                matrix.setConcat(scaleMatrix,preMatrix);
                RectF newRect = new RectF();
                scaleMatrix.mapRect(newRect, rectF);
                rectLeft = newRect.left;
                rectTop = newRect.top;
                rectRight = newRect.right;
                rectBottom = newRect.bottom;
                outWidth = rectRight - rectLeft;
                outHeight = rectBottom - rectTop;
//                rectLeft = preRectLeft - (preRectLeft - edgingWidth)*ratio;
//                if(rectLeft < edgingWidth)
//                    rectLeft = edgingWidth;
//                rectRight =preRectRight + (width - edgingWidth -preRectRight) *ratio;
//                if(rectRight > width - edgingWidth)
//                    rectRight =width - edgingWidth;
//                rectLeft = preRectLeft + dx;
//                rectRight = preRectRight + dx;
//                outWidth = rectRight - rectLeft;
//                rectTop = preRectTop + dy;
//                rectBottom = preRectBottom + dy;
                /*RectF rectF = new RectF(rectLeft,rectTop,rectRight,rectBottom);
                matrix.mapRect(rectF);*/
                invalidate();
            }
        });
        animator.start();
        thread = null;
//            float sx,px,py,dx;
//            float dt = 10f;
//            while(rectLeft > edgingWidth||rectRight<width-edgingWidth){
//                float preWidth;
//                px = (rectRight + rectLeft)/2;
//                py = (rectBottom + rectTop)/2;
//                preWidth = rectRight - rectLeft;
//                if(rectLeft > edgingWidth)
//                    rectLeft -= dt;
//                else{
//                    rectLeft = edgingWidth;
//                }
//                if(rectRight<width-edgingWidth)
//                    rectRight += dt;
//                else{
//                    rectRight = width - edgingWidth;
//                }
//                outWidth = rectRight - rectLeft;
//                sx = outWidth/preWidth;
//                dx = (rectRight + rectLeft)/2-px;
//                matrix.postScale(sx,sx,px,py);
//                matrix.postTranslate(dx,0);
//                invalidate();
//            }
//            invalidate();


//            invalidate();
        }

   /* private void recovery(){

    }*/

    public void saveCropImagetofile(String path){
        Bitmap out = getCropImage();
        File file = new File(path);
        if(!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try{
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            out.compress(Bitmap.CompressFormat.JPEG,100,bos);
            bos.flush();
            bos.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private Bitmap getCropImage(){
        Bitmap source;
        Bitmap out;
        setDrawingCacheEnabled(true);
//        destroyDrawingCache();
        this.buildDrawingCache(true);
        this.buildDrawingCache();
        pad = false;
        invalidate();
        source = this.getDrawingCache();
        out = Bitmap.createBitmap(source,(int)(rectLeft+edgingWidth),(int)(rectTop+edgingWidth),
                (int)(outWidth-2*edgingWidth),(int)(outHeight-2*edgingWidth));
        setDrawingCacheEnabled(false);
        pad = true;
        invalidate();
        return out;
    }

    public void setImage(File file){
        if(!file.exists()){
            Toast.makeText(mContext,"file not exists",Toast.LENGTH_SHORT).show();
        }
        String path = file.getAbsolutePath();
        setImage(path);
    }



    /**
     * @param path
     * @time 123
     */
    public void setImage(String path){
        Bitmap bitmap =BitmapFactory.decodeFile(path);
        int degree = readPictureDegree(path);
        if(degree != 0){
            Matrix matrix = new Matrix();
            matrix.setRotate(degree);
            bitmap =Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
        }
        setImage(bitmap);

    }

    public int readPictureDegree(String path){
        int degree = 0;
        try{
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            switch (orientation){
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        }catch (IOException e){
            e.printStackTrace();
        }
        return degree;
    }

    @TargetApi(16)
    public void setImage(Bitmap bitmap){
        Drawable drawable = new BitmapDrawable(bitmap);
        mainBitmap = bitmap;
//        setBackground(drawable);//need api 16
    }

    public void setImage(int resid){
        setBackgroundResource(resid);
    }

    //是否画九宫格
    public void setPad(boolean pad) {
        this.pad = pad;
    }


}
