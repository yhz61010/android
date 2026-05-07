cmake_minimum_required(VERSION 4.1.2)

project("leo-bitmap")

add_library(${PROJECT_NAME} SHARED
    BitmapRotateNative.cpp
)

target_include_directories(${PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(${PROJECT_NAME}
    ${log-lib}
    ${jnigraphics-lib}
)

# Ensure 16KB page alignment for Android compatibility
target_link_options(${PROJECT_NAME} PRIVATE
    "LINKER:-z,max-page-size=16384"
)
