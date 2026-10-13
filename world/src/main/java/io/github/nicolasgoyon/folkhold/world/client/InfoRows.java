package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiMeter;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiRow;
import io.github.nicolasgoyon.folkhold.core.client.ui.UiTheme;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import java.util.List;

/** Converts the rows that the server sends into rows of the UI framework. */
final class InfoRows {
    private InfoRows() {
    }

    /** The UI rows of a section. */
    static List<UiRow> of(List<Row> rows) {
        return rows.stream().map(InfoRows::of).toList();
    }

    /** The UI row of a server row, with its meter. */
    static UiRow of(Row row) {
        UiMeter meter = switch (row.meterKind()) {
            case VillageInfoPayload.METER_BAR -> UiMeter.bar(row.meter(), row.meterMax());
            case VillageInfoPayload.METER_SEGMENTS -> UiMeter.segments(row.meter(), row.meterMax(), row.meterLost(),
                    UiTheme.GILT);
            default -> null;
        };
        return UiRow.of(row.label(), row.value(), meter);
    }
}
