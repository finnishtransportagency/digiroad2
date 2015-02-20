(function (root) {
  root.ManoeuvreForm = function() {
    var template = '' +
      '<header><span>Linkin ID: <%= sourceMmlId %></span></header>' +
      '<div class="wrapper read-only"><div class="form form-horizontal form-dark"><div></div></div></div>';
    var manouvreTemplate = '' +
      '<div class="form-group">' +
        '<label class="control-label">Kääntyminen kielletty linkille </label>' +
        '<p class="form-control-static"><%= destMmlId%></p>' +
      '</div>';
    var bindEvents = function() {
      var rootElement = $('#feature-attributes');
      eventbus.on('manoeuvres:selected manoeuvres:cancelled manoeuvres:saved', function(manoeuvres) {
        rootElement.html(_.template(template, manoeuvres[0]));
        _.each(manoeuvres, function(manouvre) {
          rootElement.find('.form').append(_.template(manouvreTemplate, manouvre));
        });
      });
    };
    bindEvents();
  };
})(this);
