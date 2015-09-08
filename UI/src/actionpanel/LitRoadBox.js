(function(root) {
 root.LitRoadBox = function() {
   var collapsedTemplate =
     '<div class="panel">' +
       '<header class="panel-header">Valaistu tie</header>' +
     '</div>';

   return {
     element: $('<div class="panel-group"/>').append(collapsedTemplate)
   };
 };
})(this);
