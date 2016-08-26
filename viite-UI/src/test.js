(function(exports) {
  //Private Property
  var isPrivate = true;

  //Public Property
  exports.publicProperty = "Bacon Strips";

  //Public Method
  exports.publicMethod = function() {
    return 1;
  };

  //Private Method
  function privateMethod( item ) {
    if ( item !== undefined ) {
      console.log( "Adding " + $.trim(item) );
    }
  }
// }(window.skillet = window.skillet || {}));
}(typeof exports === "undefined" ? (this.skillet = {}) : exports));