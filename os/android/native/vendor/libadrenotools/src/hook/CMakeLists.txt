add_library(hook_impl SHARED hook_impl.cpp hook_impl.h hook_impl_params.h)

target_compile_options(hook_impl PRIVATE -Wall -Wextra)
target_link_libraries(hook_impl linkernsbypass log)
target_include_directories(hook_impl PRIVATE ../../include)
set_target_properties(hook_impl PROPERTIES CXX_VISIBILITY_PRESET hidden)

add_library(main_hook SHARED main_hook.c)

target_compile_options(main_hook PRIVATE -Wall -Wextra)
target_link_options(main_hook PRIVATE -z global)
target_link_libraries(main_hook hook_impl)
set_target_properties(main_hook PROPERTIES CXX_VISIBILITY_PRESET hidden)

add_library(c11_threads_compat SHARED c11_threads_compat.c)

# API 30 headers declare the C11 thread functions; the definitions use only
# pthread, so the library still loads on the proxy's API 28.
target_compile_options(c11_threads_compat PRIVATE --target=aarch64-linux-android30 -Wall -Wextra)

add_library(file_redirect_hook SHARED file_redirect_hook.c)

target_compile_options(file_redirect_hook PRIVATE -Wall -Wextra)
target_link_options(file_redirect_hook PRIVATE -z global)
target_link_libraries(file_redirect_hook hook_impl)
set_target_properties(file_redirect_hook PROPERTIES CXX_VISIBILITY_PRESET hidden)

add_library(gsl_alloc_hook SHARED gsl_alloc_hook.c)

target_compile_options(gsl_alloc_hook PRIVATE -Wall -Wextra)
target_link_options(gsl_alloc_hook PRIVATE -z global)
target_link_libraries(gsl_alloc_hook hook_impl)
set_target_properties(gsl_alloc_hook PROPERTIES CXX_VISIBILITY_PRESET hidden)
