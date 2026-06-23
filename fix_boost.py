import os

base = r'd:\project\andriod\trime\app\src\main\jni\boost'
libs_needed = ['signals2', 'algorithm', 'range', 'utility', 'uuid', 'crc', 'dll', 'interprocess', 'regex', 'scope_exit']

for lib in libs_needed:
    src = os.path.join(base, 'libs', lib, 'include', 'boost', lib)
    dst = os.path.join(base, 'boost', lib)
    src_dir = os.path.dirname(src)
    if not os.path.exists(dst) and os.path.exists(src_dir):
        os.symlink(src, dst)
        print(f'Created symlink: boost/{lib} -> libs/{lib}/include/boost/{lib}')
    elif os.path.exists(dst):
        print(f'Exists (skip): {dst}')
    else:
        print(f'Not found: {src_dir}')
