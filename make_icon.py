from PIL import Image, ImageDraw

s = 512
image = Image.new('RGBA', (s, s), (0, 0, 0, 0))
draw = ImageDraw.Draw(image)
draw.rounded_rectangle((28, 28, 484, 484), radius=108, fill='#0B6853')
draw.rounded_rectangle((44, 44, 468, 468), radius=94, outline='#94E8C9', width=5)
draw.rounded_rectangle((132, 98, 380, 402), radius=34, fill='#F5FFF9')
draw.rounded_rectangle((158, 140, 296, 161), radius=9, fill='#08785E')
for y, width in ((194, 186), (227, 158), (260, 128)):
    draw.rounded_rectangle((158, y, 158 + width, y + 14), radius=7, fill='#B8DDCE')
draw.ellipse((271, 271, 421, 421), fill='#7BE0B8')
draw.ellipse((281, 281, 411, 411), outline='#F0FFF7', width=7)
draw.line((307, 347, 338, 377, 390, 319), fill='#07513F', width=16, joint='curve')
image.save('icon.ico', sizes=[(16,16), (24,24), (32,32), (48,48), (64,64), (128,128), (256,256)])
