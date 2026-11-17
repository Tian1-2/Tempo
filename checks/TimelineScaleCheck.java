import com.tempo.app.ui.TimelineScale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TimelineScaleCheck {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        TimelineScale empty = new TimelineScale(1.6f, Collections.emptyList());
        check(Math.abs(empty.y(60) - 96) < .01f, "empty hour keeps its original height");
        check(Math.abs(empty.y(1440) - 2304) < .01f, "empty day keeps its original height");
        // Device screenshot: completed 07:00–07:12, 25-minute task, 5-minute rest, 10-minute task.
        List<TimelineScale.Span> screenshot = Arrays.asList(
                new TimelineScale.Span(420, 432, 52), new TimelineScale.Span(432, 457, 52),
                new TimelineScale.Span(457, 462, 32), new TimelineScale.Span(462, 472, 52));
        TimelineScale scale = new TimelineScale(1.6f, screenshot);
        for (TimelineScale.Span span : screenshot) {
            check(scale.y(span.end) - scale.y(span.start) >= span.minimumHeight - .01f,
                    "measured task/rest text fits in its own interval");
        }
        boolean separated = true;
        for (int i = 1; i < screenshot.size(); i++) {
            TimelineScale.Span previous = screenshot.get(i - 1), next = screenshot.get(i);
            float previousTextBottom = scale.y(previous.start) + 2 + previous.minimumHeight - 4;
            separated &= scale.y(next.start) + 2 >= previousTextBottom + 4 - .01f;
        }
        check(separated, "text of adjacent tasks and rest fits before the next card with a gutter");
        check(scale.y(457) < scale.y(460) && scale.y(460) < scale.y(462),
                "current-time marker within rest stays between its real boundaries");
        boolean increasing = true;
        for (int minute = 1; minute <= 1440; minute++) increasing &= scale.y(minute) > scale.y(minute - 1);
        check(increasing, "all minute positions remain strictly chronological");
        List<TimelineScale.Span> reversed = new ArrayList<>(screenshot); Collections.reverse(reversed);
        TimelineScale reversedScale = new TimelineScale(1.6f, reversed);
        boolean same = true;
        for (int minute = 0; minute <= 1440; minute++) same &= Math.abs(scale.y(minute) - reversedScale.y(minute)) < .01f;
        check(same, "database row order does not change the axis");
        TimelineScale largeText = new TimelineScale(1.6f, Arrays.asList(
                new TimelineScale.Span(420, 421, 144), new TimelineScale.Span(421, 426, 48),
                new TimelineScale.Span(426, 427, 112), new TimelineScale.Span(1439, 1440, 80)));
        check(largeText.y(421) - largeText.y(420) >= 144, "one-minute task can fit a wrapped title and large text");
        check(largeText.y(1440) - largeText.y(1439) >= 80, "last-minute task ends inside the day canvas");
        check(largeText.y(-1) == 0 && largeText.y(1441) == largeText.y(1440), "off-day marker positions are clamped");
        check(Math.abs(largeText.y(400) - empty.y(400)) < .01f, "earlier empty hours remain unchanged");
        boolean invalid = false;
        try { new TimelineScale.Span(10, 10, 48); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "zero-duration span is rejected");
        System.out.println("PASS: " + assertions + " timeline geometry assertions.");
    }
}
