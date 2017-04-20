{
  "exports": ["*"],
  "compile": {
  "externs": [
    "externs/geojson.js",
    "externs/oli.js",
    "externs/olx.js",
    "externs/proj4js.js",
    "externs/tilejson.js",
    "externs/topojson.js"
  ],
    "compilation_level": "SIMPLE",
    "output_wrapper": "(function(){%output%})();",
    "use_types_for_optimization": true,
    "manage_closure_dependencies": false
}
}