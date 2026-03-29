#include "plugin.h"
#include "scales.h"
#include "variations.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <math.h>

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

static const char *root_names[] = {
   "C","C#","D","D#","E","F","F#","G","G#","A","A#","B"
};

static const char *snap_names[] = {
   "Nearest","Round Up","Round Down","Pass Through"
};

static inline uint32_t clamp_u32(double v, uint32_t lo, uint32_t hi) {
   int32_t iv = (int32_t)v;
   if (iv < (int32_t)lo) return lo;
   if (iv > (int32_t)hi) return hi;
   return (uint32_t)iv;
}

static inline bool should_process(const my_plugin_t *p, int16_t channel) {
   uint32_t ich = p->params[PARAM_INPUT_CH];
   return (ich == 0) || (channel == (int16_t)(ich - 1));
}

static void sync_params(my_plugin_t *p) {
   if (!atomic_load(&p->params_dirty)) return;
   for (int i = 0; i < PARAM_COUNT; i++)
      p->params[i] = atomic_load(&p->main_params[i]);
   atomic_store(&p->params_dirty, false);
}

static void maybe_recompute_table(my_plugin_t *p) {
   if (p->params[PARAM_ROOT_NOTE] != p->table_root ||
       p->params[PARAM_SCALE]     != p->table_scale ||
       p->params[PARAM_VARIATION] != p->table_variation ||
       p->params[PARAM_SNAP]      != p->table_snap) {
      table_recompute(p->table,
                      p->params[PARAM_ROOT_NOTE],
                      p->params[PARAM_SCALE],
                      p->params[PARAM_VARIATION],
                      p->params[PARAM_SNAP]);
      p->table_root      = p->params[PARAM_ROOT_NOTE];
      p->table_scale     = p->params[PARAM_SCALE];
      p->table_variation = p->params[PARAM_VARIATION];
      p->table_snap      = p->params[PARAM_SNAP];
   }
}

static void apply_param(my_plugin_t *p, clap_id id, double value) {
   static const uint32_t maxes[PARAM_COUNT] = {11, NUM_SCALES-1, NUM_VARIATIONS-1, 3, 16, 16};
   if (id >= PARAM_COUNT) return;
   p->params[id] = clamp_u32(value, 0, maxes[id]);
   atomic_store(&p->main_params[id], p->params[id]);
}

// ---------------------------------------------------------------------------
// Plugin lifecycle
// ---------------------------------------------------------------------------

static bool plugin_init(const clap_plugin_t *plugin) {
   (void)plugin;
   return true;
}

static void plugin_destroy(const clap_plugin_t *plugin) {
   my_plugin_t *p = plugin->plugin_data;
   free(p);
}

static bool plugin_activate(const clap_plugin_t *plugin, double sr,
                            uint32_t min_frames, uint32_t max_frames) {
   (void)plugin; (void)sr; (void)min_frames; (void)max_frames;
   return true;
}

static void plugin_deactivate(const clap_plugin_t *plugin) { (void)plugin; }

static bool plugin_start_processing(const clap_plugin_t *plugin) {
   my_plugin_t *p = plugin->plugin_data;
   memset(p->active_notes, 255, sizeof(p->active_notes));
   return true;
}

static void plugin_stop_processing(const clap_plugin_t *plugin) { (void)plugin; }

static void plugin_reset(const clap_plugin_t *plugin) {
   my_plugin_t *p = plugin->plugin_data;
   memset(p->active_notes, 255, sizeof(p->active_notes));
   sync_params(p);
   maybe_recompute_table(p);
}

// ---------------------------------------------------------------------------
// Process
// ---------------------------------------------------------------------------

static clap_process_status plugin_process(const clap_plugin_t *plugin,
                                          const clap_process_t *process) {
   my_plugin_t *p = plugin->plugin_data;
   const clap_input_events_t *in = process->in_events;
   const clap_output_events_t *out = process->out_events;

   sync_params(p);
   maybe_recompute_table(p);

   uint32_t count = in->size(in);
   for (uint32_t i = 0; i < count; i++) {
      const clap_event_header_t *hdr = in->get(in, i);

      if (hdr->space_id != CLAP_CORE_EVENT_SPACE_ID) {
         out->try_push(out, hdr);
         continue;
      }

      switch (hdr->type) {

      case CLAP_EVENT_PARAM_VALUE: {
         const clap_event_param_value_t *ev = (const clap_event_param_value_t *)hdr;
         apply_param(p, ev->param_id, ev->value);
         maybe_recompute_table(p);
         break;
      }

      case CLAP_EVENT_NOTE_ON: {
         clap_event_note_t ev = *(const clap_event_note_t *)hdr;
         if (should_process(p, ev.channel) && ev.key >= 0 && ev.key < 128) {
            int16_t orig = ev.key;
            ev.key = p->table[orig];
            p->active_notes[ev.channel & 0xF][orig] = (uint8_t)ev.key;
            if (p->params[PARAM_OUTPUT_CH] > 0)
               ev.channel = (int16_t)(p->params[PARAM_OUTPUT_CH] - 1);
         }
         out->try_push(out, &ev.header);
         break;
      }

      case CLAP_EVENT_NOTE_OFF: {
         clap_event_note_t ev = *(const clap_event_note_t *)hdr;
         if (should_process(p, ev.channel) && ev.key >= 0 && ev.key < 128) {
            int16_t orig = ev.key;
            uint8_t mapped = p->active_notes[ev.channel & 0xF][orig];
            if (mapped != 255) {
               ev.key = mapped;
               p->active_notes[ev.channel & 0xF][orig] = 255;
            }
            if (p->params[PARAM_OUTPUT_CH] > 0)
               ev.channel = (int16_t)(p->params[PARAM_OUTPUT_CH] - 1);
         }
         out->try_push(out, &ev.header);
         break;
      }

      case CLAP_EVENT_NOTE_CHOKE: {
         clap_event_note_t ev = *(const clap_event_note_t *)hdr;
         if (should_process(p, ev.channel) && ev.key >= 0 && ev.key < 128) {
            int16_t orig = ev.key;
            uint8_t mapped = p->active_notes[ev.channel & 0xF][orig];
            if (mapped != 255) {
               ev.key = mapped;
               p->active_notes[ev.channel & 0xF][orig] = 255;
            }
            if (p->params[PARAM_OUTPUT_CH] > 0)
               ev.channel = (int16_t)(p->params[PARAM_OUTPUT_CH] - 1);
         }
         out->try_push(out, &ev.header);
         break;
      }

      case CLAP_EVENT_NOTE_EXPRESSION: {
         clap_event_note_expression_t ev = *(const clap_event_note_expression_t *)hdr;
         if (should_process(p, ev.channel) && ev.key >= 0 && ev.key < 128) {
            uint8_t mapped = p->active_notes[ev.channel & 0xF][ev.key];
            if (mapped != 255) ev.key = mapped;
            if (p->params[PARAM_OUTPUT_CH] > 0)
               ev.channel = (int16_t)(p->params[PARAM_OUTPUT_CH] - 1);
         }
         out->try_push(out, &ev.header);
         break;
      }

      case CLAP_EVENT_MIDI: {
         clap_event_midi_t ev = *(const clap_event_midi_t *)hdr;
         uint8_t status = ev.data[0] & 0xF0;
         uint8_t ch = ev.data[0] & 0x0F;

         if (should_process(p, ch) && (status == 0x90 || status == 0x80)) {
            uint8_t orig = ev.data[1] & 0x7F;
            if (status == 0x90 && ev.data[2] > 0) {
               uint8_t mapped = p->table[orig];
               p->active_notes[ch][orig] = mapped;
               ev.data[1] = mapped;
            } else {
               uint8_t mapped = p->active_notes[ch][orig];
               if (mapped != 255) {
                  ev.data[1] = mapped;
                  p->active_notes[ch][orig] = 255;
               }
            }
            if (p->params[PARAM_OUTPUT_CH] > 0)
               ev.data[0] = status | ((p->params[PARAM_OUTPUT_CH] - 1) & 0x0F);
         }
         out->try_push(out, &ev.header);
         break;
      }

      default:
         out->try_push(out, hdr);
         break;
      }
   }

   return CLAP_PROCESS_CONTINUE;
}

// ---------------------------------------------------------------------------
// Extension: Note Ports
// ---------------------------------------------------------------------------

static uint32_t note_ports_count(const clap_plugin_t *plugin, bool is_input) {
   (void)plugin;
   (void)is_input;
   return 1;
}

static bool note_ports_get(const clap_plugin_t *plugin, uint32_t index,
                           bool is_input, clap_note_port_info_t *info) {
   (void)plugin;
   if (index != 0) return false;
   info->id = is_input ? 0 : 1;
   info->supported_dialects = CLAP_NOTE_DIALECT_CLAP | CLAP_NOTE_DIALECT_MIDI;
   info->preferred_dialect = CLAP_NOTE_DIALECT_CLAP;
   snprintf(info->name, sizeof(info->name), "%s", is_input ? "Note In" : "Note Out");
   return true;
}

static const clap_plugin_note_ports_t s_note_ports = {
   .count = note_ports_count,
   .get   = note_ports_get,
};

// ---------------------------------------------------------------------------
// Extension: Params
// ---------------------------------------------------------------------------

static uint32_t params_count(const clap_plugin_t *plugin) {
   (void)plugin;
   return PARAM_COUNT;
}

static bool params_get_info(const clap_plugin_t *plugin, uint32_t index,
                            clap_param_info_t *info) {
   (void)plugin;
   if (index >= PARAM_COUNT) return false;
   memset(info, 0, sizeof(*info));
   info->id = index;
   info->flags = CLAP_PARAM_IS_STEPPED | CLAP_PARAM_IS_AUTOMATABLE;

   switch (index) {
   case PARAM_ROOT_NOTE:
      info->flags |= CLAP_PARAM_IS_ENUM;
      strncpy(info->name, "Root Note", CLAP_NAME_SIZE);
      strncpy(info->module, "Scale", CLAP_PATH_SIZE);
      info->min_value = 0; info->max_value = 11; info->default_value = 0;
      break;
   case PARAM_SCALE:
      info->flags |= CLAP_PARAM_IS_ENUM;
      strncpy(info->name, "Scale", CLAP_NAME_SIZE);
      strncpy(info->module, "Scale", CLAP_PATH_SIZE);
      info->min_value = 0; info->max_value = NUM_SCALES - 1; info->default_value = 0;
      break;
   case PARAM_VARIATION:
      info->flags |= CLAP_PARAM_IS_ENUM;
      strncpy(info->name, "Variation", CLAP_NAME_SIZE);
      strncpy(info->module, "Scale", CLAP_PATH_SIZE);
      info->min_value = 0; info->max_value = NUM_VARIATIONS - 1; info->default_value = 0;
      break;
   case PARAM_SNAP:
      info->flags |= CLAP_PARAM_IS_ENUM;
      strncpy(info->name, "Snap Direction", CLAP_NAME_SIZE);
      strncpy(info->module, "Scale", CLAP_PATH_SIZE);
      info->min_value = 0; info->max_value = 3; info->default_value = 0;
      break;
   case PARAM_INPUT_CH:
      info->flags |= CLAP_PARAM_IS_ENUM;
      strncpy(info->name, "Input Channel", CLAP_NAME_SIZE);
      strncpy(info->module, "Routing", CLAP_PATH_SIZE);
      info->min_value = 0; info->max_value = 16; info->default_value = 0;
      break;
   case PARAM_OUTPUT_CH:
      info->flags |= CLAP_PARAM_IS_ENUM;
      strncpy(info->name, "Output Channel", CLAP_NAME_SIZE);
      strncpy(info->module, "Routing", CLAP_PATH_SIZE);
      info->min_value = 0; info->max_value = 16; info->default_value = 0;
      break;
   }
   return true;
}

static bool params_get_value(const clap_plugin_t *plugin, clap_id id, double *val) {
   my_plugin_t *p = plugin->plugin_data;
   if (id >= PARAM_COUNT) return false;
   *val = (double)atomic_load(&p->main_params[id]);
   return true;
}

static bool params_value_to_text(const clap_plugin_t *plugin, clap_id id,
                                 double value, char *buf, uint32_t cap) {
   (void)plugin;
   uint32_t v = (uint32_t)value;
   switch (id) {
   case PARAM_ROOT_NOTE:
      if (v > 11) v = 0;
      snprintf(buf, cap, "%s", root_names[v]);
      return true;
   case PARAM_SCALE:
      if (v >= NUM_SCALES) v = 0;
      snprintf(buf, cap, "%s", scale_names[v]);
      return true;
   case PARAM_VARIATION:
      if (v >= NUM_VARIATIONS) v = 0;
      snprintf(buf, cap, "%s", variation_names[v]);
      return true;
   case PARAM_SNAP:
      if (v > 3) v = 0;
      snprintf(buf, cap, "%s", snap_names[v]);
      return true;
   case PARAM_INPUT_CH:
      if (v == 0) snprintf(buf, cap, "All");
      else snprintf(buf, cap, "Channel %u", v);
      return true;
   case PARAM_OUTPUT_CH:
      if (v == 0) snprintf(buf, cap, "Same as Input");
      else snprintf(buf, cap, "Channel %u", v);
      return true;
   }
   return false;
}

static bool params_text_to_value(const clap_plugin_t *plugin, clap_id id,
                                 const char *text, double *val) {
   (void)plugin;
   switch (id) {
   case PARAM_ROOT_NOTE:
      for (int i = 0; i < 12; i++) {
         if (!strcmp(text, root_names[i])) { *val = i; return true; }
      }
      return false;
   case PARAM_SCALE:
      for (int i = 0; i < NUM_SCALES; i++) {
         if (!strcmp(text, scale_names[i])) { *val = i; return true; }
      }
      return false;
   case PARAM_VARIATION:
      for (int i = 0; i < NUM_VARIATIONS; i++) {
         if (!strcmp(text, variation_names[i])) { *val = i; return true; }
      }
      return false;
   case PARAM_SNAP:
      for (int i = 0; i < 4; i++) {
         if (!strcmp(text, snap_names[i])) { *val = i; return true; }
      }
      return false;
   case PARAM_INPUT_CH:
      if (!strcmp(text, "All")) { *val = 0; return true; }
      { unsigned ch; if (sscanf(text, "Channel %u", &ch) == 1 && ch >= 1 && ch <= 16) {
         *val = ch; return true;
      }}
      return false;
   case PARAM_OUTPUT_CH:
      if (!strcmp(text, "Same as Input")) { *val = 0; return true; }
      { unsigned ch; if (sscanf(text, "Channel %u", &ch) == 1 && ch >= 1 && ch <= 16) {
         *val = ch; return true;
      }}
      return false;
   }
   return false;
}

static void params_flush(const clap_plugin_t *plugin,
                         const clap_input_events_t *in,
                         const clap_output_events_t *out) {
   (void)out;
   my_plugin_t *p = plugin->plugin_data;
   uint32_t count = in->size(in);
   for (uint32_t i = 0; i < count; i++) {
      const clap_event_header_t *hdr = in->get(in, i);
      if (hdr->space_id == CLAP_CORE_EVENT_SPACE_ID &&
          hdr->type == CLAP_EVENT_PARAM_VALUE) {
         const clap_event_param_value_t *ev = (const clap_event_param_value_t *)hdr;
         apply_param(p, ev->param_id, ev->value);
      }
   }
   maybe_recompute_table(p);
}

static const clap_plugin_params_t s_params = {
   .count          = params_count,
   .get_info       = params_get_info,
   .get_value      = params_get_value,
   .value_to_text  = params_value_to_text,
   .text_to_value  = params_text_to_value,
   .flush          = params_flush,
};

// ---------------------------------------------------------------------------
// Extension: State
// ---------------------------------------------------------------------------

static bool state_save(const clap_plugin_t *plugin, const clap_ostream_t *stream) {
   my_plugin_t *p = plugin->plugin_data;
   uint8_t hdr[5] = {'S','Q','0','1', 1};
   if (stream->write(stream, hdr, 5) != 5) return false;
   uint32_t vals[PARAM_COUNT];
   for (int i = 0; i < PARAM_COUNT; i++)
      vals[i] = atomic_load(&p->main_params[i]);
   if (stream->write(stream, vals, sizeof(vals)) != sizeof(vals)) return false;
   return true;
}

static bool state_load(const clap_plugin_t *plugin, const clap_istream_t *stream) {
   my_plugin_t *p = plugin->plugin_data;
   uint8_t hdr[5];
   if (stream->read(stream, hdr, 5) != 5) return false;
   if (memcmp(hdr, "SQ01", 4) != 0) return false;
   uint32_t vals[PARAM_COUNT];
   if (stream->read(stream, vals, sizeof(vals)) != (int64_t)sizeof(vals)) return false;
   for (int i = 0; i < PARAM_COUNT; i++)
      atomic_store(&p->main_params[i], vals[i]);
   atomic_store(&p->params_dirty, true);
   return true;
}

static const clap_plugin_state_t s_state = {
   .save = state_save,
   .load = state_load,
};

// ---------------------------------------------------------------------------
// Extension: Latency
// ---------------------------------------------------------------------------

static uint32_t latency_get(const clap_plugin_t *plugin) {
   (void)plugin;
   return 0;
}

static const clap_plugin_latency_t s_latency = {
   .get = latency_get,
};

// ---------------------------------------------------------------------------
// get_extension
// ---------------------------------------------------------------------------

static const void *plugin_get_extension(const clap_plugin_t *plugin, const char *id) {
   (void)plugin;
   if (!strcmp(id, CLAP_EXT_NOTE_PORTS)) return &s_note_ports;
   if (!strcmp(id, CLAP_EXT_PARAMS))     return &s_params;
   if (!strcmp(id, CLAP_EXT_STATE))      return &s_state;
   if (!strcmp(id, CLAP_EXT_LATENCY))    return &s_latency;
   return NULL;
}

static void plugin_on_main_thread(const clap_plugin_t *plugin) { (void)plugin; }

// ---------------------------------------------------------------------------
// Create
// ---------------------------------------------------------------------------

const clap_plugin_t *plugin_create(const clap_host_t *host) {
   my_plugin_t *p = calloc(1, sizeof(my_plugin_t));
   if (!p) return NULL;

   p->host = host;
   p->plugin.desc            = get_plugin_descriptor();
   p->plugin.plugin_data     = p;
   p->plugin.init            = plugin_init;
   p->plugin.destroy         = plugin_destroy;
   p->plugin.activate        = plugin_activate;
   p->plugin.deactivate      = plugin_deactivate;
   p->plugin.start_processing = plugin_start_processing;
   p->plugin.stop_processing = plugin_stop_processing;
   p->plugin.reset           = plugin_reset;
   p->plugin.process         = plugin_process;
   p->plugin.get_extension   = plugin_get_extension;
   p->plugin.on_main_thread  = plugin_on_main_thread;

   // Defaults: C, Major, All Degrees, Nearest, All, Same as Input
   for (int i = 0; i < PARAM_COUNT; i++) {
      p->params[i] = 0;
      atomic_store(&p->main_params[i], 0);
   }
   atomic_store(&p->params_dirty, false);

   memset(p->active_notes, 255, sizeof(p->active_notes));
   table_recompute(p->table, 0, 0, 0, 0);
   p->table_root = p->table_scale = p->table_variation = p->table_snap = 0;

   return &p->plugin;
}
