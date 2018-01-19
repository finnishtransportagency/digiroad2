window.CrosshairToggle = function(parentElement) {
  var crosshairToggle = $('<div class="crosshair-wrapper"><div class="checkbox"><label><input type="checkbox" name="crosshair" value="crosshair" checked="true"/> Kohdistin</label></div></div>');

  var render = function() { parentElement.append(crosshairToggle); };

  var bindEvents = function() {
    $('input', crosshairToggle).change(function() {
      $('.crosshair').toggle(this.checked);
    });
  };

  var show = function() {
    render();
    bindEvents();
  };
  show();
};
