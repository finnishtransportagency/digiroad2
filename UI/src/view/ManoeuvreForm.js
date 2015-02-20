(function (root) {
  root.ManoeuvreForm = function() {
    var template = '' +
      '<header><span>Linkin ID: <%= sourceMmlId %></span></header>' +
      '<div class="wrapper read-only">' +
      '</div>';

    var bindEvents = function(){
      var rootElement = $('#feature-attributes');
      eventbus.on('manoeuvres:selected manoeuvres:cancelled manoeuvres:saved', function(manoeuvre) {
        rootElement.html(_.template(template, manoeuvre));
      });
    };
    bindEvents();
  }
})(this);
