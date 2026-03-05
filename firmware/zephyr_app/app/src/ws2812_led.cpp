#include "ws2812_led.h"

Ws2812Led::Ws2812Led(const struct device* strip)
    : strip_(strip), current_color_({0, 0, 0}), initialized_(false) {
}

bool Ws2812Led::Init() {
  if (strip_ == nullptr) {
    return false;
  }

  if (!device_is_ready(strip_)) {
    return false;
  }

  initialized_ = true;
  return true;
}

void Ws2812Led::SetColor(uint8_t r, uint8_t g, uint8_t b) {
  current_color_.r = r;
  current_color_.g = g;
  current_color_.b = b;
}

void Ws2812Led::SetColor(RgbColor color) {
  current_color_ = color;
}

RgbColor Ws2812Led::GetColor() const {
  return current_color_;
}

void Ws2812Led::Show() {
  if (!initialized_) {
    return;
  }

  struct led_rgb pixels[1];
  pixels[0].r = current_color_.r;
  pixels[0].g = current_color_.g;
  pixels[0].b = current_color_.b;

  led_strip_update_rgb(strip_, pixels, 1);
}

bool Ws2812Led::IsReady() const {
  return initialized_;
}
