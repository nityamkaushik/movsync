import { createClient } from '@supabase/supabase-js';
import { supabaseConfig } from './config.js';

let _supabase = null;

export function getSupabase() {
  if (!_supabase) {
    _supabase = createClient(supabaseConfig.url, supabaseConfig.key);
  }
  return _supabase;
}
