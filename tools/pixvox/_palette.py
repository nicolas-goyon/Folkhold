"""The colors of the Folkhold blocks: the materials of the villages (spruce, stone, iron, gold)."""
from pixvox import ramp

# Stripped spruce: the frames of the houses and the posts.
SPRUCE = ramp("#8a6844", n=5, spread=0.28, hue_shift=14)
# Spruce bark and dark spruce planks.
SPRUCE_DARK = ramp("#5a3f27", n=5, spread=0.30, hue_shift=12)
# Dark oak: the chests and the roofs.
DARK_OAK = ramp("#4c3420", n=5, spread=0.32, hue_shift=12)
# Grey stone of the bases and of the plaza.
STONE = ramp("#83858a", n=5, spread=0.20, hue_shift=10, tint=0.004)
# Deepslate of the bank walls.
DEEPSLATE = ramp("#4d4f58", n=5, spread=0.26, hue_shift=12, tint=0.006)
# Wrought iron of the bands, the frames and the rivets.
IRON = ramp("#646a73", n=5, spread=0.44, hue_shift=10, tint=0.012)
# Gold of the locks and of the emblems.
GOLD = ramp("#d1a43c", n=5, spread=0.38, hue_shift=20)
# The warm glow of the heart of a village.
EMBER = ramp("#f0a030", n=5, spread=0.40, hue_shift=24)
# Red and white paint of the archer target.
PAINT_RED = ramp("#a8322b", n=4, spread=0.28, hue_shift=12)
PAINT_WHITE = ramp("#e6dcc4", n=4, spread=0.16, hue_shift=8)
