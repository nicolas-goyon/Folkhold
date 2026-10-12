package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.List;

/**
 * What a job consumes and produces in one cycle.
 *
 * @param inputs  the items that the job consumes
 * @param outputs the items that the job produces
 */
public record Recipe(List<Input> inputs, List<Output> outputs) {

    /**
     * An item that the job consumes.
     *
     * @param item  the item id
     * @param count the quantity (1 or more)
     */
    public record Input(String item, int count) {
        public Input {
            if (item == null || item.isBlank() || count < 1) {
                throw new IllegalArgumentException("Invalid input: " + item + " x" + count);
            }
        }
    }

    /**
     * An item that the job produces.
     *
     * @param item   the item id
     * @param count  the quantity (1 or more)
     * @param chance the chance (0 to 1) of each unit. The job produces each unit with this chance.
     */
    public record Output(String item, int count, double chance) {
        public Output {
            if (item == null || item.isBlank() || count < 1 || chance <= 0.0 || chance > 1.0) {
                throw new IllegalArgumentException("Invalid output: " + item + " x" + count + " " + chance);
            }
        }
    }

    public Recipe {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("A recipe needs at least one output");
        }
    }
}
