(function(root) {
 root.LitRoadBox = function() {
   var layerName = 'litRoad';
   var collapsed =
     $('<div class="panel">' +
         '<header class="panel-header">Valaistu tie</header>' +
       '</div>');

   var expanded =
     $('<div class="panel">' +
         '<header class="panel-header expanded">Valaistu tie</header>' +
       '</div>').hide();

   var executeOrShowConfirmDialog = function(f) {
     if (applicationModel.isDirty()) {
       new Confirm();
     } else {
       f();
     }
   };

   var bindDOMEventHandlers = function() {
     collapsed.click(function() {
       executeOrShowConfirmDialog(function() {
         collapsed.hide();
         expanded.show();
       });
     });
   };

   bindDOMEventHandlers();

   var bindExternalEventHandlers = function() {
     eventbus.on('layer:selected', function(selectedLayer) {
       if (selectedLayer !== layerName) {
         expanded.hide();
         collapsed.show();
       } else {
         collapsed.hide();
         expanded.show();
       }
     }, this);
   };
   bindExternalEventHandlers();

   return {
     element: $('<div class="panel-group"/>')
       .append(collapsed)
       .append(expanded)
   };
 };
})(this);
