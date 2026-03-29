cmake_minimum_required(VERSION 3.14)
project(note-fx-scale C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# CLAP SDK (header-only)
add_library(clap-sdk INTERFACE)
target_include_directories(clap-sdk INTERFACE ${CMAKE_SOURCE_DIR}/clap/include)

# Plugin shared library
add_library(note-fx-scale MODULE
    src/entry.c
    src/plugin.c
    src/table.c
)

target_link_libraries(note-fx-scale PRIVATE clap-sdk)

# Platform-specific output naming
if(WIN32)
    set_target_properties(note-fx-scale PROPERTIES
        PREFIX ""
        SUFFIX ".clap"
    )
elseif(APPLE)
    set_target_properties(note-fx-scale PROPERTIES
        PREFIX ""
        SUFFIX ""
        BUNDLE TRUE
        BUNDLE_EXTENSION "clap"
    )
else()
    # Linux: output as .clap (renamed .so)
    set_target_properties(note-fx-scale PROPERTIES
        PREFIX ""
        SUFFIX ".clap"
    )
endif()

# Symbol visibility: only export clap_entry
if(NOT WIN32)
    target_compile_options(note-fx-scale PRIVATE -fvisibility=hidden)
endif()
