"""
Audio Synthesizer for PrintGuard AI Cinematic Showcase Video
Generates a synchronized, professional electronic soundtrack with:
- Warm analog sub-bass drone
- Driving 16th-note synth ostinato arpeggio (120 BPM)
- Cinematic sub-bass impacts at scene reveals
- High-tech transition whoosh risers
- Subtle digital telemetry clicks and UI blips
Outputs a high-quality 44.1kHz 16-bit stereo WAV file.
"""

import math
import wave
import struct
import numpy as np

def generate_soundtrack(output_path="video-generator/temp/soundtrack.wav", total_duration=66.0, sample_rate=44100):
    total_samples = int(total_duration * sample_rate)
    t = np.linspace(0, total_duration, total_samples, endpoint=False)
    
    # Left and Right stereo channels
    left = np.zeros(total_samples, dtype=np.float32)
    right = np.zeros(total_samples, dtype=np.float32)
    
    # -------------------------------------------------------------
    # 1. Warm Analog Ambient Drone / Pad (Root: A1 = 55 Hz, E2 = 82.4 Hz, A2 = 110 Hz)
    # -------------------------------------------------------------
    # Slow LFO filter modulation (0.1 Hz)
    lfo = 0.5 + 0.5 * np.sin(2 * np.pi * 0.08 * t)
    
    drone_a1 = np.sin(2 * np.pi * 55.0 * t) * 0.22
    drone_e2 = np.sin(2 * np.pi * 82.41 * t + 0.3) * 0.14
    drone_a2 = np.sin(2 * np.pi * 110.0 * t + 0.7) * (0.08 + 0.06 * lfo)
    drone_harm = np.sin(2 * np.pi * 220.0 * t) * (0.03 * lfo)
    
    drone_mono = drone_a1 + drone_e2 + drone_a2 + drone_harm
    
    # Stereo widening for pad
    left += drone_mono * 0.95
    right += np.roll(drone_mono, int(sample_rate * 0.015)) * 0.95
    
    # -------------------------------------------------------------
    # 2. Driving 16th-Note Tech Arpeggio / Ostinato (120 BPM)
    # Starts introducing gently at t=6s, drives hard through t=55s, fades at outro
    # -------------------------------------------------------------
    bpm = 120.0
    beat_dur = 60.0 / bpm          # 0.5s per quarter note
    sixteenth_dur = beat_dur / 4.0 # 0.125s per sixteenth note
    
    # Notes in A minor / tech pentatonic: A3(220), C4(261.6), D4(293.7), E4(329.6), G4(392)
    arp_scale = [220.0, 261.63, 293.66, 329.63, 392.00, 440.0, 329.63, 293.66]
    
    arp_start = 6.0
    arp_end = 56.0
    num_steps = int((arp_end - arp_start) / sixteenth_dur)
    
    for i in range(num_steps):
        step_time = arp_start + i * sixteenth_dur
        step_idx = int(step_time * sample_rate)
        note_freq = arp_scale[i % len(arp_scale)]
        
        # Energy crescendo over time
        progress = (step_time - arp_start) / (arp_end - arp_start)
        base_vol = 0.06 + 0.10 * np.clip(progress, 0.0, 1.0)
        
        # Pluck envelope: sharp attack, exponential decay over 0.10s
        note_samples = int(0.12 * sample_rate)
        if step_idx + note_samples >= total_samples:
            break
            
        t_note = np.linspace(0, 0.12, note_samples, endpoint=False)
        envelope = np.exp(-t_note * 24.0)
        
        # Sawtooth-like filtered tone
        tone = (np.sin(2 * np.pi * note_freq * t_note) * 0.7 +
                np.sin(2 * np.pi * note_freq * 2 * t_note) * 0.2 +
                np.sin(2 * np.pi * note_freq * 3 * t_note) * 0.1) * envelope * base_vol
        
        # Alternate stereo panning for rhythmic bounce
        pan = 0.5 + 0.3 * np.sin(i * 0.75)
        left[step_idx:step_idx + note_samples] += tone * (1.0 - pan)
        right[step_idx:step_idx + note_samples] += tone * pan
        
        # Add subtle stereo ping-pong delay tail (80ms delay)
        delay_samples = int(0.08 * sample_rate)
        d_idx = step_idx + delay_samples
        if d_idx + note_samples < total_samples:
            left[d_idx:d_idx + note_samples] += tone * pan * 0.35
            right[d_idx:d_idx + note_samples] += tone * (1.0 - pan) * 0.35

    # -------------------------------------------------------------
    # 3. Cinematic Sub-Bass Impact Booms (Scene reveals: 0s, 6s, 14s, 22s, 34s, 44s, 55s)
    # -------------------------------------------------------------
    impact_times = [0.1, 6.0, 14.0, 22.0, 34.0, 44.0, 55.0]
    for hit_time in impact_times:
        hit_idx = int(hit_time * sample_rate)
        hit_dur = 2.2
        hit_samples = int(hit_dur * sample_rate)
        if hit_idx + hit_samples >= total_samples:
            continue
            
        t_hit = np.linspace(0, hit_dur, hit_samples, endpoint=False)
        # Pitch down-sweep: starts at 90Hz drops to 38Hz
        freq_sweep = 38.0 + 52.0 * np.exp(-t_hit * 3.5)
        phase = 2 * np.pi * np.cumsum(freq_sweep) / sample_rate
        sub_envelope = np.exp(-t_hit * 2.0)
        sub_boom = np.sin(phase) * sub_envelope * 0.38
        
        # Punch transient click (10ms)
        click_dur = 0.02
        c_samples = int(click_dur * sample_rate)
        t_c = np.linspace(0, click_dur, c_samples, endpoint=False)
        click = np.sin(2 * np.pi * 320.0 * t_c) * np.exp(-t_c * 150.0) * 0.18
        
        left[hit_idx:hit_idx + hit_samples] += sub_boom
        right[hit_idx:hit_idx + hit_samples] += sub_boom
        left[hit_idx:hit_idx + c_samples] += click
        right[hit_idx:hit_idx + c_samples] += click

    # -------------------------------------------------------------
    # 4. Filtered White Noise Transition Risers (Whooshes into scenes)
    # -------------------------------------------------------------
    riser_targets = [5.9, 13.9, 21.9, 33.9, 43.9, 54.9]
    for r_target in riser_targets:
        r_dur = 1.4
        r_start = r_target - r_dur
        r_idx = int(r_start * sample_rate)
        r_samples = int(r_dur * sample_rate)
        if r_idx < 0 or r_idx + r_samples >= total_samples:
            continue
            
        t_r = np.linspace(0, 1.0, r_samples, endpoint=False)
        # Exponential volume rise
        r_env = (t_r ** 2.2) * 0.15
        
        # White noise modulated by rising pitch
        noise = np.random.uniform(-1.0, 1.0, r_samples)
        # Rising resonant sweep
        sweep_freq = 200.0 + 3500.0 * (t_r ** 2)
        sine_sweep = np.sin(2 * np.pi * np.cumsum(sweep_freq) / sample_rate) * 0.5
        
        combined_riser = (noise * 0.5 + sine_sweep * 0.5) * r_env
        
        # Pan sweep from left to right
        left[r_idx:r_idx + r_samples] += combined_riser * (1.0 - t_r * 0.5)
        right[r_idx:r_idx + r_samples] += combined_riser * (0.5 + t_r * 0.5)

    # -------------------------------------------------------------
    # 5. UI Digital Telemetry Clicks & Beeps (Scenes 4 & 5 Pipeline stages)
    # -------------------------------------------------------------
    ui_beeps = [
        # Scene 4 Pipeline activations
        23.2, 25.2, 27.2, 29.2, 31.2,
        # Scene 5 workflow stages
        35.0, 37.0, 39.0, 41.0,
        # Scene 6 architecture cards
        45.0, 47.0, 49.0, 51.0
    ]
    for b_time in ui_beeps:
        b_idx = int(b_time * sample_rate)
        b_dur = 0.04
        b_samples = int(b_dur * sample_rate)
        if b_idx + b_samples >= total_samples:
            continue
            
        t_b = np.linspace(0, b_dur, b_samples, endpoint=False)
        # Dual-frequency high-tech chirp
        chirp = (np.sin(2 * np.pi * 1760.0 * t_b) * 0.6 + np.sin(2 * np.pi * 3520.0 * t_b) * 0.4) * np.exp(-t_b * 75.0) * 0.06
        left[b_idx:b_idx + b_samples] += chirp * 0.7
        right[b_idx:b_idx + b_samples] += chirp * 0.7

    # -------------------------------------------------------------
    # 6. Master Fade In (first 1s) and Fade Out (last 2.5s)
    # -------------------------------------------------------------
    fade_in_samples = int(1.2 * sample_rate)
    fade_in_curve = np.linspace(0.0, 1.0, fade_in_samples)
    left[:fade_in_samples] *= fade_in_curve
    right[:fade_in_samples] *= fade_in_curve
    
    fade_out_samples = int(2.5 * sample_rate)
    fade_out_curve = np.linspace(1.0, 0.0, fade_out_samples)
    left[-fade_out_samples:] *= fade_out_curve
    right[-fade_out_samples:] *= fade_out_curve
    
    # -------------------------------------------------------------
    # 7. Master Limiter / Normalization
    # -------------------------------------------------------------
    max_val = max(np.max(np.abs(left)), np.max(np.abs(right)), 1e-6)
    target_peak = 0.92
    left = (left / max_val) * target_peak
    right = (right / max_val) * target_peak
    
    # Interleave to 16-bit PCM stereo
    left_int16 = (left * 32767).astype(np.int16)
    right_int16 = (right * 32767).astype(np.int16)
    interleaved = np.empty((total_samples * 2,), dtype=np.int16)
    interleaved[0::2] = left_int16
    interleaved[1::2] = right_int16
    
    with wave.open(output_path, "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(interleaved.tobytes())
        
    print(f"Soundtrack generated: {output_path} ({total_duration:.1f}s, {sample_rate}Hz stereo)")
    return output_path

if __name__ == "__main__":
    generate_soundtrack()
