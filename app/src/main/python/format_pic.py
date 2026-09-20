from PIL import Image
import io
import numpy as np

def get_best_ico_size(width, height):
    """
    根据图片的最小边长，返回最适合的 ICO 尺寸。

    参数:
        width: 图片宽度。
        height: 图片高度。

    返回:
        int: 最适合的 ICO 尺寸（如 256, 128, 64 等）。
    """
    # ICO 格式支持的常见尺寸
    ico_sizes = [256, 128, 64, 48, 32, 16]

    # 计算图片的最小边长
    min_side = min(width, height)

    # 找到最接近的标准尺寸
    for size in ico_sizes:
        if min_side >= size:
            return size
    return ico_sizes[-1]  # 如果图片太小，返回最小的尺寸

def convert_image(input_image, output_path, target_format):
    """
    将输入的图片转换为指定格式，并保存到指定路径。

    参数:
        input_image: 输入的图片，可以是 Bitmap 或 byte[] 格式。
        output_path: 转换后图片的保存路径（绝对路径）。
        target_format: 目标图片格式，支持 'png', 'bmp', 'webp', 'ico', 'jpeg', 'gif'。

    返回:
        bool: 转换是否成功。
    """
    try:
        # 如果输入是 byte[]，将其转换为 PIL Image
        if isinstance(input_image, bytes):
            image = Image.open(io.BytesIO(input_image))
        # 如果输入是 Bitmap（假设为 numpy 数组），将其转换为 PIL Image
        elif isinstance(input_image, np.ndarray):
            image = Image.fromarray(input_image)
        else:
            raise ValueError("不支持的输入格式，请输入 Bitmap 或 byte[] 格式的图片。")

        # 根据目标格式处理图片
        target_format = target_format.lower()  # 统一转为小写
        if target_format not in ['png', 'bmp', 'webp', 'ico', 'jpeg', 'gif']:
            raise ValueError("不支持的图片格式，请选择 'png', 'bmp', 'webp', 'ico', 'jpeg', 'gif'。")

        # 如果是 ICO 格式，进行特殊处理
        if target_format == 'ico':
            # 获取图片的宽度和高度
            width, height = image.size

            # 获取最适合的 ICO 尺寸
            ico_size = get_best_ico_size(width, height)

            # 裁剪图片为正方形
            min_side = min(width, height)
            left = (width - min_side) / 2
            top = (height - min_side) / 2
            right = (width + min_side) / 2
            bottom = (height + min_side) / 2
            cropped_image = image.crop((left, top, right, bottom))

            # 缩放图片到最适合的 ICO 尺寸
            resized_image = cropped_image.resize((ico_size, ico_size), Image.ANTIALIAS)

            # 保存为 ICO 格式
            resized_image.save(output_path, format="ico")
        else:
            # 对于其他格式，直接保存
            if target_format == 'jpeg':
                image = image.convert("RGB")  # JPEG 不支持透明度，需要转换为 RGB 模式
            image.save(output_path, format=target_format)

        return True
    except Exception as e:
        print(f"图片转换失败: {e}")
        return False