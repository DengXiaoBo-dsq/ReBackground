import io
import os
import numpy as np
import cv2
from PIL import Image


def optimize_edges1(image_bytes, mask_bytes, output_path=None):
    """
    优化 Alpha 遮罩并合成透明背景图像

    参数:
        image_bytes (bytes): 原图的字节流（PNG格式）
        mask_bytes (bytes): 掩码的字节流（PNG格式）
        output_path (str, optional): 透明背景图像的保存路径

    返回:
        bytes: 透明背景图像的字节流（PNG格式）
    """
    # 将字节流转换为 PIL 图像
    image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    mask = Image.open(io.BytesIO(mask_bytes)).convert("L")  # 转为灰度图

    # 转换为 NumPy 数组
    image_np = np.array(image)
    mask_np = np.array(mask)

    # ------------------------- 边缘优化步骤 -------------------------
    # 1. 高斯模糊
    mask_smoothed = cv2.GaussianBlur(mask_np, (5, 5), sigmaX=2, sigmaY=2)

    # 2. 使用Canny边缘检测提取边缘
    edges = cv2.Canny(mask_smoothed, threshold1=100, threshold2=200)

    # 3. 对边缘进行形态学操作（细化）
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3))
    edges_dilated = cv2.dilate(edges, kernel, iterations=1)
    edges_eroded = cv2.erode(edges_dilated, kernel, iterations=1)

    # 4. 将边缘与原始掩码结合
    mask_refined = cv2.bitwise_or(mask_smoothed, edges_eroded)

    # 5. 使用双边滤波平滑边缘
    mask_float = mask_refined.astype(np.float32) / 255.0
    mask_refined = cv2.bilateralFilter(mask_float, d=9, sigmaColor=75, sigmaSpace=75)

    # 6. 对边缘区域进行透明度平滑过渡
    alpha = mask_refined * 255
    alpha[alpha < 10] = 0  # 完全透明的区域
    alpha[alpha >= 10] = np.clip(alpha[alpha >= 10], 0, 255)  # 平滑过渡

    # 7. 直接使用 OpenCV 合成透明背景
    rgba = np.dstack((image_np, alpha.astype(np.uint8)))  # 合成 RGBA 图像

    # 转换为 PIL 图像并保存
    result_image = Image.fromarray(rgba.astype(np.uint8), mode="RGBA")
    byte_stream = io.BytesIO()
    result_image.save(byte_stream, format="PNG")
    result_bytes = byte_stream.getvalue()

    # 保存到指定路径（如果提供）
    if output_path:
        # 确保路径是应用的私有目录

        result_image.save(output_path, format="PNG")

    return result_bytes


def optimize_edges(image_bytes, mask_bytes, output_path=None):
    """
    优化 Alpha 遮罩并合成透明背景图像

    参数:
        image_bytes (bytes): 原图的字节流（PNG格式）
        mask_bytes (bytes): 掩码的字节流（PNG格式）
        output_path (str, optional): 透明背景图像的保存路径

    返回:
        bytes: 透明背景图像的字节流（PNG格式）
    """
    # 将字节流转换为 PIL 图像
    image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    mask = Image.open(io.BytesIO(mask_bytes)).convert("L")  # 转为灰度图

    # 转换为 NumPy 数组
    image_np = np.array(image)
    mask_np = np.array(mask)

    # ------------------------- 边缘优化步骤 -------------------------
    # 1. 高斯模糊
    mask_smoothed = cv2.GaussianBlur(mask_np, (5, 5), sigmaX=2, sigmaY=2)

    # 2. 使用Canny边缘检测提取边缘
    edges = cv2.Canny(mask_smoothed, threshold1=50, threshold2=100)

    # 3. 对边缘进行形态学操作（细化）
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (1, 1))
    edges_dilated = cv2.dilate(edges, kernel, iterations=5)
    edges_eroded = cv2.erode(edges_dilated, kernel, iterations=5)

    # 4. 将边缘与原始掩码结合
    mask_refined = cv2.bitwise_or(mask_smoothed, edges_eroded)

    # 5. 使用双边滤波平滑边缘
    mask_float = mask_refined.astype(np.float32) / 255.0
    mask_refined = cv2.bilateralFilter(mask_float, d=1, sigmaColor=45, sigmaSpace=45)

    # 6. 对边缘区域进行透明度平滑过渡
    alpha = mask_refined * 255
    alpha[alpha < 60] = 0  # 完全透明的区域
    alpha[alpha >= 20] = np.clip(alpha[alpha >= 10], 0, 255)  # 平滑过渡

    # 7. 使用距离变换生成透明度渐变
    dist_transform = cv2.distanceTransform(mask_refined.astype(np.uint8), cv2.DIST_L2, 5)
    dist_transform = cv2.normalize(dist_transform, None, 0, 255, cv2.NORM_MINMAX)
    alpha = np.clip(alpha + dist_transform, 0, 255).astype(np.uint8)

    # 8. 使用图像修复技术修复边缘区域
    inpaint_mask = cv2.bitwise_not(mask_refined.astype(np.uint8))
    inpainted = cv2.inpaint(image_np, inpaint_mask, inpaintRadius=6, flags=cv2.INPAINT_TELEA)

    # 9. 直接使用 OpenCV 合成透明背景
    rgba = np.dstack((inpainted, alpha))  # 合成 RGBA 图像

    # 转换为 PIL 图像并保存
    result_image = Image.fromarray(rgba.astype(np.uint8), mode="RGBA")
    byte_stream = io.BytesIO()
    result_image.save(byte_stream, format="PNG")
    result_bytes = byte_stream.getvalue()

    # 保存到指定路径（如果提供）
    if output_path:
        result_image.save(output_path, format="PNG")

    return result_bytes