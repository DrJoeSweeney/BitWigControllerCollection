#pragma once

#include <clap/clap.h>
#include <stdint.h>
#include <stdatomic.h>
#include <stdbool.h>

enum {
   PARAM_ROOT_NOTE  = 0,
   PARAM_SCALE      = 1,
   PARAM_VARIATION  = 2,
   PARAM_SNAP       = 3,
   PARAM_INPUT_CH   = 4,
   PARAM_OUTPUT_CH  = 5,
   PARAM_COUNT      = 6,
};

enum {
   SNAP_NEAREST  = 0,
   SNAP_UP       = 1,
   SNAP_DOWN     = 2,
   SNAP_THROUGH  = 3,
};

typedef struct {
   clap_plugin_t             plugin;
   const clap_host_t        *host;

   // Audio-thread parameter copies
   uint32_t                  params[PARAM_COUNT];

   // Main-thread parameter copies (atomic for cross-thread sync)
   atomic_uint               main_params[PARAM_COUNT];
   atomic_bool               params_dirty;

   // Precomputed note translation table
   uint8_t                   table[128];

   // Active note tracking: [channel][original_key] -> mapped_key, 255 = none
   uint8_t                   active_notes[16][128];

   // Cached values for dirty detection
   uint32_t                  table_root;
   uint32_t                  table_scale;
   uint32_t                  table_variation;
   uint32_t                  table_snap;
} my_plugin_t;

// plugin.c
const clap_plugin_t *plugin_create(const clap_host_t *host);

// entry.c
const clap_plugin_descriptor_t *get_plugin_descriptor(void);

// table.c
void table_recompute(uint8_t table[128], uint32_t root, uint32_t scale_idx,
                     uint32_t variation_idx, uint32_t snap);
