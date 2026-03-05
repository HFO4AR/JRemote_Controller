#ifndef WS2812_LED_H
#define WS2812_LED_H

#include <zephyr/device.h>
#include <zephyr/drivers/led_strip.h>

// RGB color structure
struct RgbColor {
  uint8_t r;
  uint8_t g;
  uint8_t b;
};

// WS2812 LED class wrapping Zephyr LED strip driver
class Ws2812Led {
 public:
  // Constructor
  explicit Ws2812Led(const struct device* strip);

  // Initialize the LED
  bool Init();

  // Set color (RGB)
  void SetColor(uint8_t r, uint8_t g, uint8_t b);

  // Set color (from RgbColor struct)
  void SetColor(RgbColor color);

  // Get current color
  RgbColor GetColor() const;

  // Show (update LED)
  void Show();

  // Check if LED is ready
  bool IsReady() const;

 private:
  const struct device* strip_;
  RgbColor current_color_;
  bool initialized_;
};

#endif  // WS2812_LED_H
