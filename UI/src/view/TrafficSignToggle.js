(function (root) {
  root.TrafficSignToggle = function(map, container) {
    var element =
      $('<div class="sign-toggle-container">' +
        '<div class="checkbox-wrapper">'+
        '<div class="checkbox">' +
        '<label><input type="checkbox"/>Näytä maastossa kuvatut liikennemerkit</label>' +
        '</div>' +
        '</div>' +
        '</div>');

    container.append(element);

    element.find('.checkbox').find('input[type=checkbox]').on('change', function (event) {
      if ($(event.currentTarget).prop('checked')) {
        eventbus.trigger('showVioniceSigns');
      } else {
        if (applicationModel.isDirty()) {
          $(event.currentTarget).prop('checked', true);
          new Confirm();
        } else {
          eventbus.trigger('hideVioniceSigns');
        }
      }
    });
  };
})(this);
