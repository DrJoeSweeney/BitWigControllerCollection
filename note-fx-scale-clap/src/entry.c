#include <clap/clap.h>
#include <string.h>
#include "plugin.h"

static const char *s_features[] = {
   CLAP_PLUGIN_FEATURE_NOTE_EFFECT,
   NULL,
};

static const clap_plugin_descriptor_t s_descriptor = {
   .clap_version = CLAP_VERSION_INIT,
   .id           = "com.docjoe.note-fx-scale",
   .name         = "Note FX Scale Quantizer",
   .vendor       = "DocJoe",
   .url          = "",
   .manual_url   = "",
   .support_url  = "",
   .version      = "2.0.0",
   .description  = "Quantizes MIDI notes to a selected scale and mode",
   .features     = s_features,
};

const clap_plugin_descriptor_t *get_plugin_descriptor(void) {
   return &s_descriptor;
}

// Factory
static uint32_t factory_count(const clap_plugin_factory_t *f) {
   (void)f;
   return 1;
}

static const clap_plugin_descriptor_t *factory_get(
   const clap_plugin_factory_t *f, uint32_t index) {
   (void)f;
   return (index == 0) ? &s_descriptor : NULL;
}

static const clap_plugin_t *factory_create(
   const clap_plugin_factory_t *f,
   const clap_host_t *host,
   const char *plugin_id) {
   (void)f;
   if (strcmp(plugin_id, s_descriptor.id) != 0) return NULL;
   return plugin_create(host);
}

static const clap_plugin_factory_t s_factory = {
   .get_plugin_count      = factory_count,
   .get_plugin_descriptor = factory_get,
   .create_plugin         = factory_create,
};

// Entry
static bool entry_init(const char *path) { (void)path; return true; }
static void entry_deinit(void) {}
static const void *entry_get_factory(const char *id) {
   if (!strcmp(id, CLAP_PLUGIN_FACTORY_ID)) return &s_factory;
   return NULL;
}

CLAP_EXPORT const clap_plugin_entry_t clap_entry = {
   .clap_version = CLAP_VERSION_INIT,
   .init         = entry_init,
   .deinit       = entry_deinit,
   .get_factory  = entry_get_factory,
};
