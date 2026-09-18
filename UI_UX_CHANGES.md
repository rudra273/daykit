# UI/UX changes

This brief records the agreed UI pass and the supplied light and dark screenshots.

## Requested changes

### Home screen

- Show only the icon and title on each tool card. Remove the description text.
- **Keep the current height and width of every home tool card.** Adjust the content layout inside the cards only.
- Reorder the **Productivity** tools: Dayflow → Habits → Focus → Expenses.
- Reorder the **Utilities** tools: Reminders → Document Scanner → Editor → DNS Manager → Event Light.

### Bottom navigation

- Reduce the height of the Home / Today / Settings navigation bar.
- Give the selected tab a clear, subtle animation. Explore an icon shape or state change, with motion that feels quick and polished.

### Buttons and empty states

- Reduce button sizes where the current controls feel oversized. Keep labels readable and touch targets comfortable.
- On the empty Habits screen, remove the large enclosing card. Present the icon, message, and action button directly in the page layout.
- Do the same for the empty Document Scanner screen.
- Review the reference images for any other places where a card container adds visual weight without helping the content.

## Visual direction

- Keep the existing Facebook-inspired feel as the reference for the theme: clean surfaces, restrained blue accents, simple hierarchy, and clear selected states.
- Favor consistent spacing, icon sizing, button proportions, and corner shapes across screens.
- Refine dark mode with a charcoal page, slightly lighter cards, softer dividers, brighter accent icons, and a darker blue for filled actions.
- Use outlined icons on inactive bottom tabs and filled icons on the active tab, with a short scale and color animation instead of a large selection pill.

## Additional ideas to consider

- Use one consistent style for empty-state icons, text, and primary actions across tools.
- Make pressed, selected, and disabled states consistent so controls give clear feedback.
- Check light and dark themes after the changes so contrast and emphasis remain balanced.
- Use restrained motion and respect the device's reduced-motion setting where possible.

## Implementation decisions

- Keep Settings and Dayflow cards. Remove the large containers from all four Habits empty states and the Document Scanner introduction.
- Keep the home card dimensions by reserving the former description line as blank space inside each card.
- Make the navigation content 60dp tall plus the system navigation inset; keep labels visible.
- Make primary, secondary, and destructive buttons visually 36dp tall with smaller horizontal padding.
- Review Home, Today, Settings, Habits, Document Scanner, App Lock, and Dayflow in both themes, including enlarged text and disabled animations.

## Verification

- `:app:assembleDebug` and `:app:testDebugUnitTest` pass.
- The filled action colors meet 4.5:1 contrast with white text in both themes.
- Device screenshot review remains to be done when an Android device or emulator is available.
