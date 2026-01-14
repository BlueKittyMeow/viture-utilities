#!/usr/bin/env python3
import pygame
import sys
import os

# Force pygame to use the second display (HDMI-1 / Viture glasses)
os.environ['SDL_VIDEO_WINDOW_POS'] = '1920,0'  # Position on second monitor

pygame.init()

# Borderless window at full size - not pygame "fullscreen"
screen = pygame.display.set_mode((1920, 1080), pygame.NOFRAME)
pygame.display.set_caption("VITURE HUD")

font = pygame.font.Font(None, 36)

lines = ["Type here..."]
current_line = 0

hud_x, hud_y = 50, 600
hud_w, hud_h = 500, 400

running = True
clock = pygame.time.Clock()

while running:
    for event in pygame.event.get():
        if event.type == pygame.QUIT:
            running = False
        elif event.type == pygame.KEYDOWN:
            if event.key == pygame.K_ESCAPE:
                running = False
            elif event.key == pygame.K_RETURN:
                lines.append("")
                current_line += 1
            elif event.key == pygame.K_BACKSPACE:
                if lines[current_line]:
                    lines[current_line] = lines[current_line][:-1]
                elif current_line > 0:
                    current_line -= 1
                    lines.pop()
            else:
                if event.unicode:
                    lines[current_line] += event.unicode
    
    # Pure black background
    screen.fill((0, 0, 0))
    
    # Quit button (since no window controls)
    pygame.draw.rect(screen, (100, 0, 0), (10, 10, 50, 40))
    btn_text = font.render("X", True, (255, 100, 100))
    screen.blit(btn_text, (25, 15))
    
    # Text area
    pygame.draw.rect(screen, (0, 30, 0), (hud_x, hud_y, hud_w, hud_h))
    pygame.draw.rect(screen, (0, 255, 0), (hud_x, hud_y, hud_w, hud_h), 2)
    
    # Draw lines
    y_offset = hud_y + 10
    visible_lines = lines[-12:]
    for i, line in enumerate(visible_lines):
        cursor = "_" if i == len(visible_lines)-1 else ""
        text_surface = font.render(line + cursor, True, (0, 255, 0))
        screen.blit(text_surface, (hud_x + 10, y_offset))
        y_offset += 32
    
    pygame.display.flip()
    clock.tick(30)

pygame.quit()