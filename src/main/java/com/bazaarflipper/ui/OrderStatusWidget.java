package com.bazaarflipper.ui;

import com.bazaarflipper.engine.OrderManager;
import com.bazaarflipper.util.ColorUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

public class OrderStatusWidget {

    private final OrderManager orderManager;

    public OrderStatusWidget(OrderManager orderManager) {
        this.orderManager = orderManager;
    }

    public void render(GuiGraphics context, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        context.fill(x, y, x+width, y+height, ColorUtils.PANEL_BG);
        context.fill(x-1, y-1, x+width+1, y+height+1, ColorUtils.PANEL_BORDER);

        int rowY = y+5;
        for (var entry : orderManager.getAllActiveOrders().entrySet()) {
            String productId = entry.getKey();
            OrderManager.ActiveOrder order = entry.getValue();
            context.drawString(mc.font, productId, x+5, rowY, ColorUtils.ITEM_NAME, false);
            String status = order.staleStatus.name();
            context.drawString(mc.font, status, x+100, rowY, ColorUtils.SECONDARY_TEXT, false);
            String fill = String.format("%.0f%%", order.fillPercentage*100);
            context.drawString(mc.font, fill, x+150, rowY, ColorUtils.PRIMARY_TEXT, false);

            // Progress bar
            context.fill(x+5, rowY+10, x+width-5, rowY+13, ColorUtils.PROGRESS_BG);
            int fillW = (int)((width-10) * order.fillPercentage);
            context.fill(x+5, rowY+10, x+5+fillW, rowY+13, ColorUtils.PROGRESS_FILL);

            rowY += 20;
            if (rowY > y+height-20) break;
        }
    }
}
