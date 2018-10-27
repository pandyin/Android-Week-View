package com.alamkanak.weekview.drawing;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import com.alamkanak.weekview.model.WeekViewConfig;
import com.alamkanak.weekview.model.WeekViewEvent;
import com.alamkanak.weekview.ui.WeekView;

import java.util.Calendar;
import java.util.List;

public class EventsDrawer {

    private WeekViewConfig config;
    private WeekViewDrawingConfig drawingConfig;
    private EventChipRectCalculator chipRectCalculator;

    public EventsDrawer(WeekViewConfig config) {
        this.config = config;
        this.drawingConfig = config.drawingConfig;
        this.chipRectCalculator = new EventChipRectCalculator(config);
    }

    // TODO: Unify both methods?

    void drawEvents(List<EventChip> eventChips,
                    Calendar date, float startFromPixel, Canvas canvas) {
        if (eventChips == null) {
            return;
        }

        for (int i = 0; i < eventChips.size(); i++) {
            EventChip eventChip = eventChips.get(i);
            WeekViewEvent event = eventChip.event;
            if (!event.isSameDay(date)) {
                continue;
            }

            RectF chipRect = chipRectCalculator.calculate(eventChip, startFromPixel);
            if (isValidSingleEventRect(chipRect)) {
                eventChip.rectF = chipRect;
                drawingConfig.eventBackgroundPaint.setColor(event.getColor() == 0 ? drawingConfig.defaultEventColor : event.getColor());
                canvas.drawRoundRect(eventChip.rectF, config.eventCornerRadius, config.eventCornerRadius, drawingConfig.eventBackgroundPaint);
                drawEventTitle(event, eventChip.rectF, canvas, eventChip.rectF.top, eventChip.rectF.left);
            } else {
                eventChip.rectF = null;
            }
        }
    }

    /**
     * Draw all the all-day events of a particular day.
     *
     * @param date           The day.
     * @param startFromPixel The left position of the day area. The events will never go any left from this value.
     * @param canvas         The canvas to drawTimeColumn upon.
     */
    void drawAllDayEvents(List<EventChip> eventChips,
                          Calendar date, float startFromPixel, Canvas canvas) {
        if (eventChips == null) {
            return;
        }

        for (int i = 0; i < eventChips.size(); i++) {
            EventChip eventChip = eventChips.get(i);
            WeekViewEvent event = eventChip.event;
            if (!event.isSameDay(date)) {
                continue;
            }

            //RectF chipRect = chipRectCalculator.calculateAllDay(eventChip, startFromPixel);

            // TODO: Code quality
            // Calculate top.
            float top = config.headerRowPadding * 2 + drawingConfig.headerMarginBottom + drawingConfig.timeTextHeight / 2 + config.eventMarginVertical;

            // Calculate bottom.
            float bottom = top + eventChip.bottom;

            // Calculate left and right.
            float left = startFromPixel + eventChip.left * drawingConfig.widthPerDay;
            if (left < startFromPixel) {
                left += config.overlappingEventGap;
            }
            float right = left + eventChip.width * drawingConfig.widthPerDay;
            if (right < startFromPixel + drawingConfig.widthPerDay) {
                right -= config.overlappingEventGap;
            }

            boolean hasNoOverlaps = (right == startFromPixel + drawingConfig.widthPerDay);
            if (config.numberOfVisibleDays == 1 && hasNoOverlaps) {
                right -= config.eventMarginHorizontal * 2;
            }

            // Draw the event and the event name on top of it.
            // TODO: Code quality
            if (isValidAllDayEventRect(left, top, right, bottom)) {
                // TODO: What if multiple all-day events?

                RectF initialRect = new RectF(left, top, right, bottom);
                int lineHeight = calculateTextHeight(event, initialRect, top, left);
                int chipHeight = lineHeight + (config.eventPadding * 2) + 1;

                eventChip.rectF = new RectF(left, top, right, top + chipHeight);
                drawingConfig.setEventBackgroundColorOrDefault(event);
                canvas.drawRoundRect(eventChip.rectF, config.eventCornerRadius, config.eventCornerRadius, drawingConfig.eventBackgroundPaint);
                drawEventTitle(event, eventChip.rectF, canvas, top, left);
            } else {
                eventChip.rectF = null;
            }
        }
    }

    private boolean isValidSingleEventRect(RectF rect) {
        float totalHeaderHeight = drawingConfig.headerHeight
                + config.headerRowPadding * 2
                + drawingConfig.headerMarginBottom;

        return rect.left < rect.right
                && rect.left < WeekView.getViewWidth()
                && rect.top < WeekView.getViewHeight()
                && rect.right > drawingConfig.headerColumnWidth
                && rect.bottom > totalHeaderHeight + drawingConfig.timeTextHeight / 2;
    }

    private boolean isValidAllDayEventRect(float left, float top, float right, float bottom) {
        return left < right
                && left < WeekView.getViewWidth()
                && top < WeekView.getViewHeight()
                && right > drawingConfig.headerColumnWidth
                && bottom > 0;
    }

    private int calculateTextHeight(WeekViewEvent event, RectF rect,
                                    float originalTop, float originalLeft) {
        boolean negativeWidth = (rect.right - rect.left - config.eventPadding * 2) < 0;
        boolean negativeHeight = (rect.bottom - rect.top - config.eventPadding * 2) < 0;
        if (negativeWidth || negativeHeight) {
            return 0;
        }

        // Prepare the name of the event.
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        if (event.getTitle() != null) {
            stringBuilder.append(event.getTitle());
            stringBuilder.setSpan(new StyleSpan(Typeface.BOLD), 0, stringBuilder.length(), 0);
        }

        // Prepare the location of the event.
        if (event.getLocation() != null) {
            stringBuilder.append(' ');
            stringBuilder.append(event.getLocation());
        }

        int availableHeight = (int) (rect.bottom - originalTop - config.eventPadding * 2);
        int availableWidth = (int) (rect.right - originalLeft - config.eventPadding * 2);

        // TODO: Code quality
        // Get text dimensions.
        StaticLayout textLayout = new StaticLayout(stringBuilder, drawingConfig.eventTextPaint,
                availableWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        int lineHeight = textLayout.getHeight() / textLayout.getLineCount();

        if (availableHeight >= lineHeight) {
            // Calculate available number of line counts.
            int availableLineCount = availableHeight / lineHeight;
            do {
                // TODO: Code quality
                // TODO: Don't truncate
                // Ellipsize text to fit into event rect.
                int availableArea = availableLineCount * availableWidth;
                CharSequence ellipsized = TextUtils.ellipsize(stringBuilder, drawingConfig.eventTextPaint, availableArea, TextUtils.TruncateAt.END);
                textLayout = new StaticLayout(ellipsized, drawingConfig.eventTextPaint, (int) (rect.right - originalLeft - config.eventPadding * 2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                // Reduce line count.
                availableLineCount--;

                // Repeat until text is short enough.
            } while (textLayout.getHeight() > availableHeight);
        }

        return lineHeight;
    }

    /**
     * Draw the name of the event on top of the event rectangle.
     *
     * @param event        The event of which the title (and location) should be drawn.
     * @param rect         The rectangle on which the text is to be drawn.
     * @param canvas       The canvas to drawTimeColumn upon.
     * @param originalTop  The original top position of the rectangle. The rectangle may have some of its portion outside of the visible area.
     * @param originalLeft The original left position of the rectangle. The rectangle may have some of its portion outside of the visible area.
     */ // TODO: Code quality (number of arguments
    private void drawEventTitle(WeekViewEvent event, RectF rect,
                                Canvas canvas, float originalTop, float originalLeft) {
        boolean negativeWidth = (rect.right - rect.left - config.eventPadding * 2) < 0;
        boolean negativeHeight = (rect.bottom - rect.top - config.eventPadding * 2) < 0;
        if (negativeWidth || negativeHeight) {
            return;
        }

        // Prepare the name of the event.
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        if (event.getTitle() != null) {
            stringBuilder.append(event.getTitle());
            stringBuilder.setSpan(new StyleSpan(Typeface.BOLD), 0, stringBuilder.length(), 0);
        }

        // Prepare the location of the event.
        if (event.getLocation() != null) {
            stringBuilder.append(' ');
            stringBuilder.append(event.getLocation());
        }

        int availableHeight = (int) (rect.bottom - originalTop - config.eventPadding * 2);
        int availableWidth = (int) (rect.right - originalLeft - config.eventPadding * 2);

        // TODO: Code quality
        // Get text dimensions.
        StaticLayout textLayout = new StaticLayout(stringBuilder, drawingConfig.eventTextPaint, availableWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        int lineHeight = textLayout.getHeight() / textLayout.getLineCount();

        if (availableHeight >= lineHeight) {
            // Calculate available number of line counts.
            int availableLineCount = availableHeight / lineHeight;
            do {
                // TODO: Code quality
                // TODO: Don't truncate
                // Ellipsize text to fit into event rect.
                int availableArea = availableLineCount * availableWidth;
                CharSequence ellipsized = TextUtils.ellipsize(stringBuilder, drawingConfig.eventTextPaint, availableArea, TextUtils.TruncateAt.END);
                textLayout = new StaticLayout(ellipsized, drawingConfig.eventTextPaint, (int) (rect.right - originalLeft - config.eventPadding * 2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                // Reduce line count.
                availableLineCount--;

                // Repeat until text is short enough.
            } while (textLayout.getHeight() > availableHeight);

            // Draw text.
            canvas.save();
            canvas.translate(originalLeft + config.eventPadding, originalTop + config.eventPadding);
            textLayout.draw(canvas);
            canvas.restore();
        }
    }

}