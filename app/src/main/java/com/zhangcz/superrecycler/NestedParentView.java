package com.zhangcz.superrecycler;

import android.animation.ValueAnimator;
import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;

/**
 * Created by zhou on 2017/10/7 17:51 .
 *
 *
 *
 * 此类内部方法执行顺序
 *
 * 1.onStartNestedScroll   ---->    onNestedScrollAccepted     ---->    onNestedPreScroll     -------------->    onNestedScroll     -------->    onStopNestedScroll
 *
 * 1.目前支持单布局的嵌套滚动
 * 2.正在添加带上拉刷新的的RecyclerView
 *
 */

public class NestedParentView extends LinearLayout implements NestedScrollingParent {

    private View mTopScrollView;//顶部可嵌套滚动View ,默认是第一个
    private int mTopHeight;//顶部可嵌套滚动View的height
    private View mScrollView;//中部滚动的View

    private FooterView mFooterView;//底部下拉刷新View
    private int mFooterHeight;//底部上啦加载footerview高度
    private int safeHeight = 0;

    String TAG = getClass().getName();

    private ValueAnimator mOffsetAnimator;

    private boolean isLoading;

    private int currntState = 0;
    public static final int STATE_INIT = 0;//初始状态
    public static final int STATE_RELASELOADMORE = 1;//释放刷新
    public static final int STATE_LOADING = 2;//加载中
    public static final int STATE_DATANONE = 3;//数据为空
    public static final int STATE_NOMORE = 4;//没有更多

    private OnLoadMoreListener mLoadMoreListener;

    public NestedParentView(Context context) {
        this(context,null);
    }

    public NestedParentView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * 开始嵌套滚动
     * @param child
     * @param target 发起这个滚动的目标view
     * @param nestedScrollAxes 滚动轴向
     * @return true 如果父类接收并处理这次嵌套滚动,如果不处理则返回false ,则后续嵌套滚动事件不向此类内部分发
     */
    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        showLog("onStartNestedScroll");
        if(nestedScrollAxes == SCROLL_AXIS_VERTICAL){
            showLog("onStartNestedScroll  --->true");
            return true;
        }else{
            showLog("onStartNestedScroll  --->false");
            return false;
        }
    }

    /**
     * 这个方法会在 onStartNestedScroll 之后调用
     * 父类实现嵌套滚动前可以在此方法中进行初始化操作,实现这个方法如果还有父类的话应该调用父类的此方法,让他的父类也进行初始化
     * @param child
     * @param target 发起这个滚动的目标view
     * @param nestedScrollAxes 滚动的轴向
     */
    @Override
    public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
        showLog("onNestedScrollAccepted");
    }

    /**
     * 表示一次嵌套滚动的结束
     * @param target 发起这次滚动的view
     */
    @Override
    public void onStopNestedScroll(View target){
        if (!isLoading && getScrollY()>mTopHeight+safeHeight){//不是曾在加载中,且具备加载更多的条件
            isLoading = !isLoading;
            setFooterViewStates(STATE_LOADING);
            mLoadMoreListener.onLoadMore();
            showLog("执行上拉加载更多!");
        }else if(getScrollY()>mTopHeight){//回滚隐藏底部
            setFooterViewStates(STATE_INIT);
            hiddenFooterView();
//            scrollBy(0,mTopHeight-getScrollY());
        }
        showLog("onStopNestedScroll");
    }

    /**
     *表示一个嵌套滚动进行
     * @param target 发起这次滚动的view
     * @param dxConsumed 已经被target消费的横向像素距离
     * @param dyConsumed 已经被target消费的纵向像素距离
     * @param dxUnconsumed 未被target消费的横向像素距离
     * @param dyUnconsumed 未被target消费的纵向像素距离
     */
    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        if(dyUnconsumed>0){//向上滚动有剩余 显示底部
            showLog("向上滚动有剩余"+dyUnconsumed);
            if(getScrollY()==mTopHeight+mFooterHeight){//底部已经显示完全不做任何操作

            }else if(getScrollY()+dyUnconsumed>mTopHeight+mFooterHeight){
                scrollBy(0,(mTopHeight+mFooterHeight)-getScrollY());
            }else{
                scrollBy(0,dyUnconsumed);
            }

            if(getScrollY()>mTopHeight+safeHeight){
                setFooterViewStates(STATE_RELASELOADMORE);
            }
//            scrollBy(0,dyUnconsumed);
        }else{//向下滚动有剩余  显示头部
            if(getScrollY()==0){//头部已经显示完全,不做任何操作

            }else if(getScrollY()+dyUnconsumed<0){
                scrollBy(0,-getScrollY());
            }else{
                scrollBy(0,dyUnconsumed);
            }
            showLog("向下滚动有剩余"+dyUnconsumed);
        }
        showLog("onNestedScroll");
    }

    /**
     *表示目标view滚动之前.
     * @param target 发起这次滚动的view
     * @param dx x方向滚动的像素距离
     * @param dy y方向滚动的像素距离
     * @param consumed 被父类横向和纵向消费的距离
     */

    int countDy=0;
    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        boolean isCanHiddenTop = dy>0 && getScrollY()<mTopHeight;
        boolean isCanShowTop = dy<0 && getScrollY()>0 && ViewCompat.canScrollVertically(target,-1);
        boolean isCanHidenFooter = getScrollY()>mTopHeight;
        if(isCanHiddenTop||isCanShowTop||isCanHidenFooter){
            int consumedDy=0;
            if(dy>0){//向上滚动 直接做隐藏头部操作  隐藏完毕就不消耗这次滚动了
                if(getScrollY()+dy>mTopHeight){
                    consumedDy = mTopHeight-getScrollY();
                }else{
                    consumedDy = dy;
                }
                countDy += consumedDy;
                Log.i(TAG,"---->dy"+dy);
                Log.i(TAG,"---->getScrollY()"+getScrollY());
                Log.i(TAG,"-------->countDy"+countDy);
//            target.getLayoutParams().height = target.getLayoutParams().height+consumedDy;
//            target.invalidate();
            }else{//向下滚动 先隐藏底部.完事则,由子布局自己处理,继而再交由本布局处理,确保列表滚到第一行才移出头部,代码移动到 onNestedScroll方法中
                if(getScrollY()>mTopHeight){
                    if(getScrollY()+dy>mTopHeight){
                        consumedDy = dy;
                    }else{
                        consumedDy = mTopHeight-getScrollY();
                    }
                    if(getScrollY()<mTopHeight+safeHeight){
                        setFooterViewStates(STATE_INIT);
                    }
                    showLog("-------------------------->向下滚动,隐藏底部");
                }
            }
            if(consumedDy!=0){//等于0时没必要滚动
                scrollBy(0,consumedDy);
                consumed[1] = consumedDy;
            }
        }
        showLog("onNestedPreScroll");
    }

    /**
     *
     * @param target 发起这次滚动的view
     * @param velocityX x轴上的速度,像素每秒
     * @param velocityY y轴上的速度,像素每秒
     * @param consumed 如果children view消费了则是true,否则是false
     * @return
     */
    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        showLog("onNestedFling");
        return false;
    }

    /**
     * @return 如果这个父视图决定提前消耗这次fling,则返回true,否则返回false
     * @param target 发起这次滚动的view
     * @param velocityX x轴上的速度,像素每秒
     * @param velocityY y轴上的速度,像素每秒
     */
    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        showLog("onNestedPreFling");
        return false;
    }

    /**
     * @return 返回当前注册的嵌套滚动轴
     * 1.SCROLL_AXIS_NONE  当前没有注册的嵌套滚动轴
     * 2.SCROLL_AXIS_VERTICAL  当前注册为沿纵向嵌套滚动
     * 3.SCROLL_AXIS_HORIZONTAL  当前注册为沿横向滚动.
     */
    @Override
    public int getNestedScrollAxes() {
        showLog("getNestedScrollAxes");
        return 0;
    }

    private void showLog(String logStr){
        Log.i(TAG,logStr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTopScrollView = getChildAt(0);
        mScrollView = getChildAt(getChildCount()-2);
        mFooterView = (FooterView) getChildAt(getChildCount()-1);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if(mTopScrollView !=null){
            mTopHeight = mTopScrollView.getMeasuredHeight();
        }else{
            showLog("mTopScrollView cant be null");
        }
        if(mFooterView != null){
            mFooterHeight = mFooterView.getMeasuredHeight();
            safeHeight = mFooterHeight * 2 /3;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//        getChildAt(0).measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        ViewGroup.LayoutParams params = mScrollView.getLayoutParams();
        params.height = getMeasuredHeight();
        setMeasuredDimension(getMeasuredWidth(), mTopScrollView.getMeasuredHeight() + mFooterView.getMeasuredHeight() + mScrollView.getMeasuredHeight());
    }

    /**
     * 根据设定执行动画滚动此view
     * @param from 从哪个坐标
     * @param to 滚动到哪个坐标
     * @param speed 要滚动的速度
     */
    private void animateScroll(int from,int to,final int speed) {
        if (mOffsetAnimator == null) {
            mOffsetAnimator = new ValueAnimator();
            mOffsetAnimator.setInterpolator(new LinearInterpolator());
            mOffsetAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (animation.getAnimatedValue() instanceof Integer) {
                        scrollTo(0, (Integer) animation.getAnimatedValue());
                    }
                }
            });
        }
        mOffsetAnimator.setDuration(Math.abs((from-to)/speed)*4);
        mOffsetAnimator.setIntValues(from, to);
        mOffsetAnimator.start();
    }

    private void setFooterViewStates(int state){//设置底部footer通用状态的view
        if(currntState == state || ( isLoading && state != STATE_LOADING)){
            return ;
        }else{
            currntState = state;
        }
        switch (state){
            case STATE_INIT:
                mFooterView.onInit();
                break;
            case STATE_RELASELOADMORE:
                mFooterView.onRelaseLoadMore();
                break;
            case STATE_LOADING:
                mFooterView.onLoading();
                break;
            case STATE_DATANONE:
                mFooterView.onDataNone();
                break;
            case STATE_NOMORE:
                mFooterView.onNoMore();
                break;
        }
    }

    public interface OnLoadMoreListener{
        void onLoadMore();
    }

    //加载更多完成时调用,用来改变FooterView状态及性状
    public void setLoadMoreFinish(int state){
        isLoading = false;
        setFooterViewStates(state);
        hiddenFooterView();
    }

    private void hiddenFooterView(){
        animateScroll(getScrollY(),mTopHeight,1);
    }

    public void setLoadMoreListener(OnLoadMoreListener loadMoreListener){
        this.mLoadMoreListener = loadMoreListener;
    }

}
