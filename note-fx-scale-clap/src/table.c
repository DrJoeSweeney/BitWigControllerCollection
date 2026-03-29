#include "plugin.h"
#include "scales.h"
#include "variations.h"
#include <string.h>

static int find_nearest(const int *valid, int count, int note)
{
   // Binary search
   int lo = 0, hi = count - 1;
   while (lo <= hi) {
      int mid = (lo + hi) / 2;
      if (valid[mid] == note) return valid[mid];
      if (valid[mid] < note) lo = mid + 1;
      else hi = mid - 1;
   }
   // lo = insertion point
   int below = (lo > 0) ? valid[lo - 1] : -1;
   int above = (lo < count) ? valid[lo] : -1;
   if (below < 0) return above;
   if (above < 0) return below;
   int db = note - below;
   int da = above - note;
   return (db <= da) ? below : above;  // ties round down
}

static int find_up(const int *valid, int count, int note)
{
   int lo = 0, hi = count - 1;
   while (lo <= hi) {
      int mid = (lo + hi) / 2;
      if (valid[mid] == note) return valid[mid];
      if (valid[mid] < note) lo = mid + 1;
      else hi = mid - 1;
   }
   if (lo < count) return valid[lo];
   return valid[count - 1];  // clamp to highest
}

static int find_down(const int *valid, int count, int note)
{
   int lo = 0, hi = count - 1;
   while (lo <= hi) {
      int mid = (lo + hi) / 2;
      if (valid[mid] == note) return valid[mid];
      if (valid[mid] < note) lo = mid + 1;
      else hi = mid - 1;
   }
   if (lo > 0) return valid[lo - 1];
   return valid[0];  // clamp to lowest
}

void table_recompute(uint8_t table[128], uint32_t root, uint32_t scale_idx,
                     uint32_t variation_idx, uint32_t snap)
{
   // Pass through
   if (snap == SNAP_THROUGH) {
      for (int i = 0; i < 128; i++) table[i] = (uint8_t)i;
      return;
   }

   // Clamp indices
   if (scale_idx >= NUM_SCALES) scale_idx = 0;
   if (variation_idx >= NUM_VARIATIONS) variation_idx = 0;
   if (root > 11) root = 0;

   // Get scale intervals (terminated by -1)
   const int8_t *intervals = scale_intervals[scale_idx];
   int interval_count = 0;
   while (interval_count < 12 && intervals[interval_count] >= 0)
      interval_count++;

   // Get variation degrees (terminated by 0)
   const int8_t *degrees = variation_degrees[variation_idx];

   // Build active intervals from variation filter
   int8_t active[12];
   int active_count = 0;

   if (degrees[0] == 0) {
      // "All Degrees" — use all scale intervals
      memcpy(active, intervals, interval_count);
      active_count = interval_count;
   } else {
      for (int i = 0; i < 7 && degrees[i] != 0; i++) {
         int deg = degrees[i];  // 1-indexed
         if (deg >= 1 && deg <= interval_count) {
            active[active_count++] = intervals[deg - 1];
         }
      }
      if (active_count == 0) {
         // Fallback to all intervals
         memcpy(active, intervals, interval_count);
         active_count = interval_count;
      }
   }

   // Build sorted valid note set across all octaves
   bool valid_bitmap[128];
   memset(valid_bitmap, 0, sizeof(valid_bitmap));

   for (int octave = -1; octave <= 10; octave++) {
      for (int a = 0; a < active_count; a++) {
         int note = (int)root + octave * 12 + active[a];
         if (note >= 0 && note < 128)
            valid_bitmap[note] = true;
      }
   }

   int valid_list[128];
   int valid_count = 0;
   for (int i = 0; i < 128; i++) {
      if (valid_bitmap[i])
         valid_list[valid_count++] = i;
   }

   // Map each input note to nearest valid note
   for (int i = 0; i < 128; i++) {
      if (valid_count == 0) {
         table[i] = (uint8_t)i;
         continue;
      }
      int mapped;
      switch (snap) {
         case SNAP_NEAREST: mapped = find_nearest(valid_list, valid_count, i); break;
         case SNAP_UP:      mapped = find_up(valid_list, valid_count, i); break;
         case SNAP_DOWN:    mapped = find_down(valid_list, valid_count, i); break;
         default:           mapped = i; break;
      }
      table[i] = (uint8_t)(mapped < 0 ? 0 : (mapped > 127 ? 127 : mapped));
   }
}
