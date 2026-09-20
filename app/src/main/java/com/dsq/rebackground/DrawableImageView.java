//package com.dsq.rebackground;
//
//import android.content.Context;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Paint;
//import android.graphics.Path;
//import android.util.AttributeSet;
//import android.widget.ImageView;
//
//public class DrawableImageView extends androidx.appcompat.widget.AppCompatImageView {
//
//    private Path path; // 用于存储用户绘制的路径
//    private Paint pathPaint; // 用于绘制红色轨迹线
//
//    public DrawableImageView(Context context) {
//        super(context);
//        init();
//    }
//
//    public DrawableImageView(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        init();
//    }
//
//    public DrawableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
//        super(context, attrs, defStyleAttr);
//        init();
//    }
//
//    private void init() {
//        path = new Path();
//        pathPaint = new Paint();
//        pathPaint.setAntiAlias(true);
//        pathPaint.setColor(Color.RED); // 设置轨迹线颜色为红色
//        pathPaint.setStyle(Paint.Style.STROKE);
//        pathPaint.setStrokeWidth(5); // 设置轨迹线宽度
//    }
//
//    // 设置路径
//    public void setPath(Path path) {
//        this.path = path;
//        invalidate(); // 刷新视图
//    }
//
//    @Override
//    protected void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
//        // 绘制红色轨迹线
//        if (path != null) {
//            canvas.drawPath(path, pathPaint);
//        }
//    }
//}

package com.dsq.rebackground;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

import java.util.List;

public class DrawableImageView extends AppCompatImageView {

    private Path path; // 用于存储用户绘制的路径
    private Paint pathPaint; // 用于绘制红色轨迹线

    public DrawableImageView(Context context) {
        super(context);
        init();
    }

    public DrawableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        path = new Path();
        pathPaint = new Paint();
        pathPaint.setAntiAlias(true);
        pathPaint.setColor(Color.RED); // 设置轨迹线颜色为红色
        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeWidth(5); // 设置轨迹线宽度
    }

    // 设置路径
    public void setPath(Path path) {
        this.path = path;
        invalidate(); // 刷新视图
    }


    // 在DrawableImageView类中修改
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 获取 FreeCropActivity 的实例
        FreeCropActivity activity = (FreeCropActivity) getContext();

        // 绘制所有已保存路径
        for (Path p : activity.getPathList()) {
            canvas.drawPath(p, pathPaint);
        }

        // 绘制当前路径
        canvas.drawPath(activity.getCurrentPath(), pathPaint);
    }
//    @Override
//    protected void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
//        // 绘制红色轨迹线
//        if (path != null) {
//            canvas.drawPath(path, pathPaint);
//        }
//    }
}