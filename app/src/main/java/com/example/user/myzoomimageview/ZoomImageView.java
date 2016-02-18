package com.example.user.myzoomimageview;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Scroller;


public class ZoomImageView extends ImageView implements ScaleGestureDetector.OnScaleGestureListener,
        View.OnTouchListener , ViewTreeObserver.OnGlobalLayoutListener{
    /**
     * 缩放手势的监测
     */
    private ScaleGestureDetector mScaleGestureDetector;
    /**
     * 监听手势
     */
    private GestureDetector mGestureDetector;
    /**
     * 对图片进行缩放平移的Matrix
     */
    private Matrix mScaleMatrix;
    /**
     * 第一次加载图片时调整图片缩放比例，使图片的宽或者高充满屏幕
     */
    private boolean mFirst;
    /**
     * 图片的初始化比例
     */
    private float mInitScale;
    /**
     * 图片的最大比例
     */
    private float mMaxScale;
    /**
     * 双击图片放大的比例
     */
    private float mMidScale;
    /**
     * 最小缩放比例
     */
    private float mMinScale;
    /**
     * 最大溢出值
     */
    private float mMaxOverScale;

    /**
     * 是否正在自动放大或者缩小
     */
    private boolean isAutoScale;

    //-----------------------------------------------
    /**
     * 上一次触控点的数量
     */
    private int mLastPointerCount;
    /**
     * 是否可以拖动
     */
    private boolean isCanDrag;
    /**
     * 上一次滑动的x和y坐标
     */
    private float mLastX;
    private float mLastY;
    /**
     * 可滑动的临界值
     */
    private int mTouchSlop;
    /**
     * 是否用检查左右边界
     */
    private boolean isCheckLeftAndRight;
    /**
     * 是否用检查上下边界
     */
    private boolean isCheckTopAndBottom;
    /**
     * 速度追踪器
     */
    private VelocityTracker mVelocityTracker;
    private FlingRunnable mFlingRunnable;


    public ZoomImageView(Context context) {
        this(context, null, 0);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        //一定要将图片的ScaleType设置成Matrix类型的
        setScaleType(ScaleType.MATRIX);
        //初始化缩放手势监听器
        mScaleGestureDetector = new ScaleGestureDetector(context,this);
        //初始化矩阵
        mScaleMatrix = new Matrix();
        setOnTouchListener(this);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        //初始化手势检测器，监听双击事件
        mGestureDetector = new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                //如果是正在自动缩放，则直接返回，不进行处理
                if (isAutoScale) return true;
                //得到点击的坐标
                float x = e.getX();
                float y = e.getY();
                //如果当前图片的缩放值小于指定的双击缩放值
                if (getScale() < mMidScale){
                    //进行自动放大
                    post(new AutoScaleRunnable(mMidScale,x,y));
                }else{
                    //当前图片的缩放值大于初试缩放值，则自动缩小
                    post(new AutoScaleRunnable(mInitScale,x,y));
                }
                return true;
            }
        });



    }

    /**
     * 当view添加到window时调用，早于onGlobalLayout，因此可以在这里注册监听器
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    /**
     * 当view从window上移除时调用，因此可以在这里移除监听器
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeGlobalOnLayoutListener(this);
    }

    /**
     * 当布局树发生变化时会调用此方法，我们可以在此方法中获得控件的宽和高
     */
    @Override
    public void onGlobalLayout() {
        //只有当第一次加载图片的时候才会进行初始化，用一个变量mFirst控制
        if (!mFirst){
            mFirst = true;
            //得到控件的宽和高
            int width = getWidth();
            int height = getHeight();
            //得到当前ImageView中加载的图片
            Drawable d = getDrawable();
            if(d == null){//如果没有图片，则直接返回
                return;
            }
            //得到当前图片的宽和高，图片的宽和高不一定等于控件的宽和高
            //因此我们需要将图片的宽和高与控件宽和高进行判断
            //将图片完整的显示在屏幕中
            int dw = d.getIntrinsicWidth();
            int dh = d.getIntrinsicHeight();
            //我们定义一个临时变量，根据图片与控件的宽高比例，来确定这个最终缩放值
            float scale = 1.0f;
            //如果图片宽度大于控件宽度，图片高度小于控件高度
            if (dw>width && dh<height){
                //我们需要将图片宽度缩小，缩小至控件的宽度
                //至于为什么要这样计算，我们可以这样想
                //我们调用matrix.postScale（scale,scale）时，宽和高都要乘以scale的
                //当前我们的图片宽度是dw，dw*scale=dw*（width/dw）=width,这样就等于控件宽度了
                //我们的高度同时也乘以scale，这样能够保证图片的宽高比不改变，图片不变形
                scale = width * 1.0f / dw;

            }
            //如果图片的宽度小于控件宽度，图片高度大于控件高度
            if (dw<width && dh>height){
                //我们就应该将图片的高度缩小，缩小至控件的高度，计算方法同上
                scale = height * 1.0f / dh;
            }
            //如果图片的宽度小于控件宽度，高度小于控件高度时，我们应该将图片放大
            //比如图片宽度是控件宽度的1/2 ，图片高度是控件高度的1/4
            //如果我们将图片放大4倍，则图片的高度是和控件高度一样了，但是图片宽度就超出控件宽度了
            //因此我们应该选择一个最小值，那就是将图片放大2倍，此时图片宽度等于控件宽度
            //同理，如果图片宽度大于控件宽度，图片高度大于控件高度，我们应该将图片缩小
            //缩小的倍数也应该为那个最小值
            if ((dw < width && dh < height) || (dw > width && dh > height)){
                scale = Math.min(width * 1.0f / dw , height * 1.0f / dh);
            }

            //我们还应该对图片进行平移操作，将图片移动到屏幕的居中位置
            //控件宽度的一半减去图片宽度的一半即为图片需要水平移动的距离
            //高度同理，大家可以画个图看一看
            int dx = width/2 - dw/2;
            int dy = height/2 - dh/2;
            //对图片进行平移，dx和dy分别表示水平和竖直移动的距离
            mScaleMatrix.postTranslate(dx, dy);
            //对图片进行缩放，scale为缩放的比例，后两个参数为缩放的中心点
            mScaleMatrix.postScale(scale, scale, width / 2, height / 2);
            //将矩阵作用于我们的图片上，图片真正得到了平移和缩放
            setImageMatrix(mScaleMatrix);

            //初始化一下我们的几个缩放的边界值
            mInitScale = scale;
            //最大比例为初始比例的4倍
            mMaxScale = mInitScale * 4;
            //双击放大比例为初始化比例的2倍
            mMidScale = mInitScale * 2;
            //最小缩放比例为初试比例的1/4倍
            mMinScale = mInitScale / 4;
            //最大溢出值为最大值的5被
            mMaxOverScale = mMaxScale * 5;

        }
    }

    /**
     * 获得图片当前的缩放比例值
     */
    private float getScale(){
        //Matrix为一个3*3的矩阵，一共9个值
        float[] values = new float[9];
        //将Matrix的9个值映射到values数组中
        mScaleMatrix.getValues(values);
        //拿到Matrix中的MSCALE_X的值，这个值为图片宽度的缩放比例，因为图片高度
        //的缩放比例和宽度的缩放比例一致，我们取一个就可以了
        //我们还可以 return values[Matrix.MSCALE_Y];
        return values[Matrix.MSCALE_X];
    }

    /**
     * 获得缩放后图片的上下左右坐标以及宽高
     */
    private RectF getMatrixRectF(){
        //获得当钱图片的矩阵
        Matrix matrix = mScaleMatrix;
        //创建一个浮点类型的矩形
        RectF rectF = new RectF();
        //得到当前的图片
        Drawable d = getDrawable();
        if (d != null){
            //使这个矩形的宽和高同当前图片一致
            rectF.set(0,0,d.getIntrinsicWidth(),d.getIntrinsicHeight());
            //将矩阵映射到矩形上面，之后我们可以通过获取到矩阵的上下左右坐标以及宽高
            //来得到缩放后图片的上下左右坐标和宽高
            matrix.mapRect(rectF);
        }
        return rectF;
    }

    /**
     * 当缩放时检查边界并且使图片居中
     */
    private void checkBorderAndCenterWhenScale(){
        if (getDrawable() == null){
            return;
        }
        //初始化水平和竖直方向的偏移量
        float deltaX = 0.0f;
        float deltaY = 0.0f;
        //得到控件的宽和高
        int width = getWidth();
        int height = getHeight();
        //拿到当前图片对应的矩阵
        RectF rectF = getMatrixRectF();
        //如果当前图片的宽度大于控件宽度，当前图片处于放大状态
        if (rectF.width() >= width){
            //如果图片左边坐标是大于0的，说明图片左边离控件左边有一定距离，
            //左边会出现一个小白边
            if (rectF.left > 0){
                //我们将图片向左边移动
                deltaX = -rectF.left;
            }
            //如果图片右边坐标小于控件宽度，说明图片右边离控件右边有一定距离，
            //右边会出现一个小白边
            if (rectF.right <width){
                //我们将图片向右边移动
                deltaX = width - rectF.right;
            }
        }
        //上面是调整宽度，这是调整高度
        if (rectF.height() >= height){
            //如果上面出现小白边，则向上移动
            if (rectF.top > 0){
                deltaY = -rectF.top;
            }
            //如果下面出现小白边，则向下移动
            if (rectF.bottom < height){
                deltaY = height - rectF.bottom;
            }
        }
        //如果图片的宽度小于控件的宽度，我们要对图片做一个水平的居中
        if (rectF.width() < width){
            deltaX = width/2f - rectF.right + rectF.width()/2f;
        }

        //如果图片的高度小于控件的高度，我们要对图片做一个竖直方向的居中
        if (rectF.height() < height){
            deltaY = height/2f - rectF.bottom + rectF.height()/2f;
        }
        //将平移的偏移量作用到矩阵上
        mScaleMatrix.postTranslate(deltaX, deltaY);
    }

    /**
     * 平移时检查上下左右边界
     */
    private void checkBorderWhenTranslate() {
        //获得缩放后图片的相应矩形
        RectF rectF = getMatrixRectF();
        //初始化水平和竖直方向的偏移量
        float deltaX = 0.0f;
        float deltaY = 0.0f;
        //得到控件的宽度
        int width = getWidth();
        //得到控件的高度
        int height = getHeight();
        //如果是需要检查左和右边界
        if (isCheckLeftAndRight){
            //如果左边出现的白边
            if (rectF.left > 0){
                //向左偏移
                deltaX = -rectF.left;
            }
            //如果右边出现的白边
            if (rectF.right < width){
                //向右偏移
                deltaX = width - rectF.right;
            }
        }
        //如果是需要检查上和下边界
        if (isCheckTopAndBottom){
            //如果上面出现白边
            if (rectF.top > 0){
                //向上偏移
                deltaY = -rectF.top;
            }
            //如果下面出现白边
            if (rectF.bottom < height){
                //向下偏移
                deltaY = height - rectF.bottom;
            }
        }

        mScaleMatrix.postTranslate(deltaX,deltaY);
    }


    /**
     * 自动放大缩小，自动缩放的原理是使用View.postDelay()方法，每隔16ms调用一次
     * run方法，给人视觉上形成一种动画的效果
     */
    private class AutoScaleRunnable implements Runnable{
        //放大或者缩小的目标比例
        private float mTargetScale;
        //可能是BIGGER,也可能是SMALLER
        private float tempScale;
        //放大缩小的中心点
        private float x;
        private float y;
        //比1稍微大一点，用于放大
        private final float BIGGER = 1.07f;
        //比1稍微小一点，用于缩小
        private final float SMALLER = 0.93f;
        //构造方法，将目标比例，缩放中心点传入，并且判断是要放大还是缩小
        public AutoScaleRunnable(float targetScale , float x , float y){
            this.mTargetScale = targetScale;
            this.x = x;
            this.y = y;
            //如果当前缩放比例小于目标比例，说明要自动放大
            if (getScale() < mTargetScale){
                //设置为Bigger
                tempScale = BIGGER;
            }
            //如果当前缩放比例大于目标比例，说明要自动缩小
            if (getScale() > mTargetScale){
                //设置为Smaller
                tempScale = SMALLER;
            }
        }
        @Override
        public void run() {
            //这里缩放的比例非常小，只是稍微比1大一点或者比1小一点的倍数
            //但是当每16ms都放大或者缩小一点点的时候，动画效果就出来了
            mScaleMatrix.postScale(tempScale, tempScale, x, y);
            //每次将矩阵作用到图片之前，都检查一下边界
            checkBorderAndCenterWhenScale();
            //将矩阵作用到图片上
            setImageMatrix(mScaleMatrix);
            //得到当前图片的缩放值
            float currentScale = getScale();
            //如果当前想要放大，并且当前缩放值小于目标缩放值
            //或者  当前想要缩小，并且当前缩放值大于目标缩放值
            if ((tempScale > 1.0f) && currentScale < mTargetScale
                    ||(tempScale < 1.0f) && currentScale > mTargetScale){
                //每隔16ms就调用一次run方法
                postDelayed(this,16);
            }else {
                //current*scale=current*(mTargetScale/currentScale)=mTargetScale
                //保证图片最终的缩放值和目标缩放值一致
                float scale = mTargetScale / currentScale;
                mScaleMatrix.postScale(scale, scale, x, y);
                checkBorderAndCenterWhenScale();
                setImageMatrix(mScaleMatrix);
                //自动缩放结束，置为false
                isAutoScale = false;
            }
        }
    }

    /**
     * 惯性滑动
     */
    private class FlingRunnable implements Runnable{
        private Scroller mScroller;
        private int mCurrentX , mCurrentY;

        public FlingRunnable(Context context){
            mScroller = new Scroller(context);
        }

        public void cancelFling(){
            mScroller.forceFinished(true);
        }

        /**
         * 这个方法主要是从onTouch中或得到当前滑动的水平和竖直方向的速度
         * 调用scroller.fling方法，这个方法内部能够自动计算惯性滑动
         * 的x和y的变化率，根据这个变化率我们就可以对图片进行平移了
         */
        public void fling(int viewWidth , int viewHeight , int velocityX ,
                          int velocityY){
            RectF rectF = getMatrixRectF();
            if (rectF == null){
                return;
            }
            //startX为当前图片左边界的x坐标
            final int startX = Math.round(-rectF.left);
            final int minX , maxX , minY , maxY;
            //如果图片宽度大于控件宽度
            if (rectF.width() > viewWidth){
                //这是一个滑动范围[minX,maxX]，详情见下图
                minX = 0;
                maxX = Math.round(rectF.width() - viewWidth);
            }else{
                //如果图片宽度小于控件宽度，则不允许滑动
                minX = maxX = startX;
            }
            //如果图片高度大于控件高度，同理
            final int startY = Math.round(-rectF.top);
            if (rectF.height() > viewHeight){
                minY = 0;
                maxY = Math.round(rectF.height() - viewHeight);
            }else{
                minY = maxY = startY;
            }
            mCurrentX = startX;
            mCurrentY = startY;

            if (startX != maxX || startY != maxY){
                //调用fling方法，然后我们可以通过调用getCurX和getCurY来获得当前的x和y坐标
                //这个坐标的计算是模拟一个惯性滑动来计算出来的，我们根据这个x和y的变化可以模拟
                //出图片的惯性滑动
                mScroller.fling(startX,startY,velocityX,velocityY,minX,maxX,minY,maxY);
            }

        }

        /**
         * 每隔16ms调用这个方法，实现惯性滑动的动画效果
         */
        @Override
        public void run() {
            if (mScroller.isFinished()){
                return;
            }
            //如果返回true，说明当前的动画还没有结束，我们可以获得当前的x和y的值
            if (mScroller.computeScrollOffset()){
                //获得当前的x坐标
                final int newX = mScroller.getCurrX();
                //获得当前的y坐标
                final int newY = mScroller.getCurrY();
                //进行平移操作
                mScaleMatrix.postTranslate(mCurrentX-newX , mCurrentY-newY);
                checkBorderWhenTranslate();
                setImageMatrix(mScaleMatrix);

                mCurrentX = newX;
                mCurrentY = newY;
                //每16ms调用一次
                postDelayed(this,16);
            }
        }
    }

    /**
     * 这个是OnScaleGestureListener中的方法，在这个方法中我们可以对图片进行放大缩小
     */
    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        //当我们两个手指进行分开操作时，说明我们想要放大，这个scaleFactor是一个稍微大于1的数值
        //当我们两个手指进行闭合操作时，说明我们想要缩小，这个scaleFactor是一个稍微小于1的数值
        float scaleFactor = detector.getScaleFactor();
        //获得我们图片当前的缩放值
        float scale = getScale();
        //如果当前没有图片，则直接返回
        if (getDrawable() == null){
            return true;
        }
        //如果scaleFactor大于1，说明想放大，当前的缩放比例乘以scaleFactor之后小于
        //最大的缩放比例时，允许放大
        //如果scaleFactor小于1，说明想缩小，当前的缩放比例乘以scaleFactor之后大于
        //最小的缩放比例时，允许缩小
        if ((scaleFactor > 1.0f && scale * scaleFactor < mMaxOverScale)
                || scaleFactor < 1.0f && scale * scaleFactor > mMinScale){
            //边界控制，如果当前缩放比例乘以scaleFactor之后大于了最大的缩放比例
            if (scale * scaleFactor > mMaxOverScale + 0.01f){
                //则将scaleFactor设置成mMaxScale/scale
                //当再进行matrix.postScale时
                //scale*scaleFactor=scale*(mMaxScale/scale)=mMaxScale
                //最后图片就会放大至mMaxScale缩放比例的大小
                scaleFactor = mMaxOverScale / scale;
            }
            //边界控制，如果当前缩放比例乘以scaleFactor之后小于了最小的缩放比例
            //我们不允许再缩小
            if (scale * scaleFactor < mMinScale + 0.01f){
                //计算方法同上
                scaleFactor = mMinScale / scale;
            }
            //前两个参数是缩放的比例，是一个稍微大于1或者稍微小于1的数，形成一个随着手指放大
            //或者缩小的效果
            //detector.getFocusX()和detector.getFocusY()得到的是多点触控的中点
            //这样就能实现我们在图片的某一处局部放大的效果
            mScaleMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            //因为图片的缩放点不是图片的中心点了，所以图片会出现偏移的现象，所以进行一次边界的检查和居中操作
            checkBorderAndCenterWhenScale();
            //将矩阵作用到图片上
            setImageMatrix(mScaleMatrix);
        }
        return true;
    }

    /**
     * 一定要返回true
     */
    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        //当双击操作时，不允许移动图片，直接返回true
        if (mGestureDetector.onTouchEvent(event)){
            return true;
        }
        //将事件传递给ScaleGestureDetector
        mScaleGestureDetector.onTouchEvent(event);
        //用于存储多点触控产生的坐标
        float x = 0.0f;
        float y = 0.0f;
        //得到多点触控的个数
        int pointerCount = event.getPointerCount();
        //将所有触控点的坐标累加起来
        for(int i=0 ; i<pointerCount ; i++){
            x += event.getX(i);
            y += event.getY(i);
        }
        //取平均值，得到的就是多点触控后产生的那个点的坐标
        x /= pointerCount;
        y /= pointerCount;
        //如果触控点的数量变了，则置为不可滑动
        if (mLastPointerCount != pointerCount){
            isCanDrag = false;
            mLastX = x;
            mLastY = y;
        }
        mLastPointerCount = pointerCount;
        RectF rectF = getMatrixRectF();
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                //初始化速度检测器
                mVelocityTracker = VelocityTracker.obtain();
                if (mVelocityTracker != null){
                    //将当前的事件添加到检测器中
                    mVelocityTracker.addMovement(event);
                }
                //当手指再次点击到图片时，停止图片的惯性滑动
                if (mFlingRunnable != null){
                    mFlingRunnable.cancelFling();
                    mFlingRunnable = null;
                }
                isCanDrag = false;
                //当图片处于放大状态时，禁止ViewPager拦截事件，将事件传递给图片，进行拖动
                if (rectF.width() > getWidth() + 0.01f || rectF.height() > getHeight() + 0.01f){
                    if (getParent() instanceof ViewPager){
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }

                break;
            case MotionEvent.ACTION_MOVE:
                //当图片处于放大状态时，禁止ViewPager拦截事件，将事件传递给图片，进行拖动
                if (rectF.width() > getWidth() + 0.01f || rectF.height() > getHeight() + 0.01f){
                    if (getParent() instanceof ViewPager){
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                //得到水平和竖直方向的偏移量
                float dx = x - mLastX;
                float dy = y - mLastY;
                //如果当前是不可滑动的状态，判断一下是否是滑动的操作
                if (!isCanDrag){
                    isCanDrag = isMoveAction(dx,dy);
                }
                //如果可滑动
                if (isCanDrag){
                    if (getDrawable() != null){

                        if (mVelocityTracker != null){
                            //将当前事件添加到检测器中
                            mVelocityTracker.addMovement(event);
                        }

                        isCheckLeftAndRight = true;
                        isCheckTopAndBottom = true;
                        //如果图片宽度小于控件宽度
                        if (rectF.width() < getWidth()){
                            //左右不可滑动
                            dx = 0;
                            //左右不可滑动，也就不用检查左右的边界了
                            isCheckLeftAndRight = false;
                        }
                        //如果图片的高度小于控件的高度
                        if (rectF.height() < getHeight()){
                            //上下不可滑动
                            dy = 0;
                            //上下不可滑动，也就不用检查上下边界了
                            isCheckTopAndBottom = false;
                        }
                    }
                    mScaleMatrix.postTranslate(dx,dy);
                    //当平移时，检查上下左右边界
                    checkBorderWhenTranslate();
                    setImageMatrix(mScaleMatrix);
                }
                mLastX = x;
                mLastY = y;
                break;
            case MotionEvent.ACTION_UP:
                //当手指抬起时，将mLastPointerCount置0，停止滑动
                mLastPointerCount = 0;
                //如果当前图片大小小于初始化大小
                if (getScale() < mInitScale){
                    //自动放大至初始化大小
                    post(new AutoScaleRunnable(mInitScale,getWidth()/2,getHeight()/2));
                }
                //如果当前图片大小大于最大值
                if (getScale() > mMaxScale){
                    //自动缩小至最大值
                    post(new AutoScaleRunnable(mMaxScale,getWidth()/2,getHeight()/2));
                }
                if (isCanDrag){//如果当前可以滑动
                    if (mVelocityTracker != null){
                        //将当前事件添加到检测器中
                        mVelocityTracker.addMovement(event);
                        //计算当前的速度
                        mVelocityTracker.computeCurrentVelocity(1000);
                        //得到当前x方向速度
                        final float vX = mVelocityTracker.getXVelocity();
                        //得到当前y方向的速度
                        final float vY = mVelocityTracker.getYVelocity();
                        mFlingRunnable = new FlingRunnable(getContext());
                        //调用fling方法，传入控件宽高和当前x和y轴方向的速度
                        //这里得到的vX和vY和scroller需要的velocityX和velocityY的负号正好相反
                        //所以传入一个负值
                        mFlingRunnable.fling(getWidth(),getHeight(),(int)-vX,(int)-vY);
                        //执行run方法
                        post(mFlingRunnable);
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                //释放速度检测器
                if (mVelocityTracker != null){
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
        }
        return true;
    }


    /**
     * 判断是否是移动的操作
     */
    private boolean isMoveAction(float dx , float dy){
        //勾股定理，判断斜边是否大于可滑动的一个临界值
        return Math.sqrt(dx*dx + dy*dy) > mTouchSlop;
    }
}
