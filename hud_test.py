#!/usr/bin/env python3
"""
Simple HUD proof of concept - transparent window with text area
"""
import gi
gi.require_version('Gtk', '3.0')
from gi.repository import Gtk, Gdk

class HUDWindow(Gtk.Window):
    def __init__(self):
        super().__init__(title="VITURE HUD")
        
        # Enable transparency
        screen = self.get_screen()
        visual = screen.get_rgba_visual()
        if visual:
            self.set_visual(visual)
        
        self.set_app_paintable(True)
        self.connect("draw", self.on_draw)
        
        # Fullscreen and no decorations
        self.set_decorated(False)
        
        # Create a text view in the corner
        self.textview = Gtk.TextView()
        self.textview.set_size_request(400, 300)
        self.textview.modify_bg(Gtk.StateType.NORMAL, Gdk.color_parse("#111111"))
        self.textview.modify_text(Gtk.StateType.NORMAL, Gdk.color_parse("#00ff00"))
        
        # Put it in a fixed container so we can position it
        fixed = Gtk.Fixed()
        fixed.put(self.textview, 50, 500)  # Bottom-left area
        
        self.add(fixed)
        self.set_default_size(1920, 1080)

        # Position window on second display (HDMI-1 / Viture glasses)
        self.move(1920, 0)
        
    def on_draw(self, widget, cr):
        # Draw transparent black background
        cr.set_source_rgba(0, 0, 0, 1)  # Solid black = transparent in glasses
        cr.set_operator(1)  # CAIRO_OPERATOR_SOURCE
        cr.paint()
        return False

win = HUDWindow()
win.connect("destroy", Gtk.main_quit)
win.show_all()
Gtk.main()