package com.filmforest.crawler.source;

import com.filmforest.crawler.source.pkmp4.Pkmp4DetailParser;
import com.filmforest.crawler.source.pkmp4.Pkmp4ListParser;
import com.filmforest.crawler.source.pkmp4.Pkmp4ResourceParser;
import com.filmforest.crawler.source.pkmp4.Pkmp4SourceAdapter;
import com.filmforest.crawler.source.pkmp4.Pkmp4UrlBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceAdapterRegistryTest {

    @Test
    void resolvesStableCodeAndLegacySourceNameToSameAdapter() {
        var adapter = new Pkmp4SourceAdapter(new Pkmp4UrlBuilder(), new Pkmp4ListParser(),
                new Pkmp4DetailParser(new Pkmp4ResourceParser()));
        var registry = new SourceAdapterRegistry(List.of(adapter));

        assertThat(registry.require("pkmp4")).isSameAs(adapter);
        assertThat(registry.require("七味网")).isSameAs(adapter);
        assertThat(registry.require("PKMP4.XYZ")).isSameAs(adapter);
    }

    @Test
    void unsupportedSourceFailsBeforeAnyNetworkRequest() {
        var registry = new SourceAdapterRegistry(List.of());

        assertThatThrownBy(() -> registry.require("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported crawler source");
    }
}
