(function (root) {
  root.ManoeuvreForm = function() {
    var template = '' +
      '<header><span>Linkin ID: <%= mmlId %></span></header>' +
      '<div class="wrapper read-only"><div class="form form-horizontal form-dark"><div></div></div></div>';
    var manouvreTemplate = '' +
      '<div class="form-group">' +
        '<label class="control-label">Kääntyminen kielletty linkille </label>' +
        '<p class="form-control-static"><%= destMmlId %></p>' +
      '</div>';
    var bindEvents = function() {
      var rootElement = $('#feature-attributes');
      eventbus.on('manoeuvres:selected manoeuvres:cancelled manoeuvres:saved', function(roadLink) {
        rootElement.html(_.template(template, roadLink));
        _.each(roadLink.manoeuvres, function(manoeuvre) {
          rootElement.find('.form').append(_.template(manouvreTemplate, manoeuvre));
        });
      });
      eventbus.on('manoeuvres:unselected', function() {
        rootElement.empty();
      });
    };

    function toggleMode(readOnly) {
      if(!readOnly){

      }
    }

    eventbus.on('application:readOnly', toggleMode);
    bindEvents();
  };
})(this);
