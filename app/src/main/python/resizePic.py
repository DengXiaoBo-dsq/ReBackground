import cv2
import numpy as np
def resizeIamge(image_path,width,height,outpath,a_value,is_a):

    flag=cv2.INTER_AREA
    im=cv2.imread(image_path)
    height_p, width_p, channels = im.shape
    if height_p>height or width_p>width:
        flag=cv2.INTER_AREA
    else:
        flag=cv2.INTER_LANCZOS4
    img = cv2.resize(im,(width,height),interpolation=flag)
    if is_a=="true":

                # 将图片从BGR转为RGB
        image_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

        # 创建一个与RGB图像大小相同的透明度通道，值在1到255之间（例如，255为完全不透明，1为完全透明）
        alpha_channel = np.full((image_rgb.shape[0], image_rgb.shape[1]), int(a_value), dtype=np.uint8)  # 255表示完全不透明

        # 将RGB和透明度通道合并为RGBA
        image_rgba = np.dstack((image_rgb, alpha_channel))



# 保存结果
        if cv2.imwrite(outpath, cv2.cvtColor(image_rgba, cv2.COLOR_RGBA2BGRA))==True:
            return outpath
        else:
            return "falid"
    else:

        if cv2.imwrite(outpath,img)==True:
            return outpath
        else:
            return "falid"


