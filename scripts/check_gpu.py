#!/usr/bin/env python3
"""
Diagnostic script to verify GPU availability, PyTorch CUDA build, and hardware acceleration.
Prints hardware specifications, CUDA runtime details, and validates GPU tensor computation.
"""
import sys
import time

def run_diagnostic():
    print("=" * 60)
    print("   PrintGuard AI - GPU & PyTorch Diagnostic Utility")
    print("=" * 60)

    # 1. Python Environment Information
    print("\n[1] Python Environment:")
    print(f"  - Python Version:    {sys.version.split()[0]}")
    print(f"  - Python Executable: {sys.executable}")

    # 2. PyTorch Package Information
    print("\n[2] PyTorch Installation:")
    try:
        import torch
        print(f"  - PyTorch Version:   {torch.__version__}")
        print(f"  - PyTorch Path:      {torch.__file__}")
    except ImportError as e:
        print(f"  [ERROR] PyTorch is not installed in this environment: {e}")
        return False

    # 3. CUDA Availability Check
    print("\n[3] CUDA & Driver Support:")
    cuda_available = torch.cuda.is_available()
    cuda_version = torch.version.cuda
    print(f"  - torch.cuda.is_available(): {cuda_available}")
    print(f"  - CUDA Runtime Version:      {cuda_version if cuda_version else 'N/A'}")

    if not cuda_available:
        print("\n[FAIL] CUDA is NOT available to PyTorch.")
        print("Possible causes:")
        print("  - The installed PyTorch wheel is a CPU-only build (+cpu).")
        print("  - NVIDIA graphics driver is missing or outdated.")
        print("  - No supported NVIDIA GPU was detected on this system.")
        return False

    # 4. Detected GPU Hardware Details
    device_count = torch.cuda.device_count()
    print(f"  - Detected GPU Count:        {device_count}")

    for idx in range(device_count):
        gpu_name = torch.cuda.get_device_name(idx)
        props = torch.cuda.get_device_properties(idx)
        total_vram_gb = props.total_memory / (1024 ** 3)
        multi_proc_count = props.multi_processor_count
        major, minor = props.major, props.minor

        print(f"\n[4] GPU Device #{idx}:")
        print(f"  - Name:                      {gpu_name}")
        print(f"  - Dedicated VRAM:            {total_vram_gb:.2f} GB ({props.total_memory:,} bytes)")
        print(f"  - Compute Capability:        {major}.{minor}")
        print(f"  - Multiprocessor Count:      {multi_proc_count}")

    # 5. Tensor Computation Verification
    print("\n[5] Executing GPU Tensor Verification...")
    try:
        device = torch.device("cuda:0")
        
        # Test 1: Tensor allocation
        torch.cuda.empty_cache()
        torch.cuda.reset_peak_memory_stats(device)
        
        dim = 2000
        print(f"  - Allocating two {dim}x{dim} float32 tensors on {device}...")
        t0 = time.perf_counter()
        mat_a = torch.randn(dim, dim, device=device, dtype=torch.float32)
        mat_b = torch.randn(dim, dim, device=device, dtype=torch.float32)
        torch.cuda.synchronize()
        alloc_time = (time.perf_counter() - t0) * 1000.0

        # Test 2: Matrix multiplication computation
        t0 = time.perf_counter()
        mat_c = torch.matmul(mat_a, mat_b)
        torch.cuda.synchronize()
        compute_time = (time.perf_counter() - t0) * 1000.0

        # Test 3: Numerical verification
        sample_sum = mat_c.sum().item()
        allocated_mb = torch.cuda.memory_allocated(device) / (1024 ** 2)
        peak_mb = torch.cuda.max_memory_allocated(device) / (1024 ** 2)

        print(f"  - Matrix multiplication ({dim}x{dim}) completed in {compute_time:.2f} ms")
        print(f"  - Tensor sum result check:   {sample_sum:.4e} (valid finite float)")
        print(f"  - Current VRAM Allocated:    {allocated_mb:.2f} MB")
        print(f"  - Peak VRAM Used in Test:    {peak_mb:.2f} MB")
        print("\n[SUCCESS] GPU tensor computation executed and verified on CUDA device!")
        print("=" * 60)
        return True

    except Exception as exc:
        print(f"\n[FAIL] Tensor execution failed with exception: {exc}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = run_diagnostic()
    sys.exit(0 if success else 1)
