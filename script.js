const display = document.getElementById("display");
const buttons = document.querySelector(".buttons");

function appendValue(value) {
  if (display.value === "Error") display.value = "";
  display.value += value;
}

function clearDisplay() {
  display.value = "";
}

function deleteLast() {
  display.value = display.value.slice(0, -1);
}

async function calculate() {
  const expression = display.value.trim();
  if (!expression) return;

  try {
    const response = await fetch(
      `/calculate?expression=${encodeURIComponent(expression)}`
    );
    const result = await response.text();

    if (!response.ok) throw new Error(result);
    display.value = result;
  } catch (error) {
    display.value = "Error";
  }
}

buttons.addEventListener("click", (event) => {
  const button = event.target.closest("button");
  if (!button) return;

  const action = button.dataset.action;
  if (action === "clear") clearDisplay();
  else if (action === "delete") deleteLast();
  else if (action === "calculate") calculate();
  else if (button.dataset.value) appendValue(button.dataset.value);
});

document.addEventListener("keydown", (event) => {
  if (/^[0-9.+\-*/%()]$/.test(event.key)) appendValue(event.key);
  else if (event.key === "Enter" || event.key === "=") calculate();
  else if (event.key === "Backspace") deleteLast();
  else if (event.key === "Escape") clearDisplay();
});
